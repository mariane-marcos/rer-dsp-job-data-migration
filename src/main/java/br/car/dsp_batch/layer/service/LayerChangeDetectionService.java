package br.car.dsp_batch.layer.service;

import br.car.dsp_batch.geometry.GeometrySql;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Change detection between source and geo-target for geographic layers.
 * Deletes orphans on the geo-target only.
 */
@Slf4j
@Service
public class LayerChangeDetectionService {

    /** Stable alias for 2D geometry in comparison subqueries (independent of source column name). */
    static final String LAYER_GEOM_2D_ALIAS = "layer_geom_2d";

    public static final String CTX_HAS_CHANGES = "hasChanges";
    public static final String CTX_AFFECTED_BBOXES = "affectedBboxes";
    public static final String CTX_LAYER_NAME = "layerName";

    public void detectChanges(JdbcTemplate sourceJdbc,
                              JdbcTemplate geoTargetJdbc,
                              LayerTableMetadata metadata,
                              ChunkContext chunkContext) {
        LayerFeaturePersistenceService.requirePositiveSrid(metadata);
        log.info("Starting change detection for table: {}", metadata.qualifiedSourceTable());

        Map<Object, RecordComparison> geoTarget = fetchTargetData(geoTargetJdbc, metadata);
        log.info("Geo-target: {} records", geoTarget.size());

        List<String> bboxes = compareStreamingSource(sourceJdbc, metadata, geoTarget);

        if (!geoTarget.isEmpty()) {
            deleteRemovedRecords(geoTargetJdbc, metadata, geoTarget.keySet());
        }

        var jobContext = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        if (bboxes.isEmpty()) {
            log.info("No changes detected in {}", metadata.qualifiedSourceTable());
            jobContext.put(CTX_HAS_CHANGES, false);
        } else {
            log.info("Detected {} areas with changes in {}",
                    bboxes.size(), metadata.qualifiedSourceTable());
            jobContext.put(CTX_HAS_CHANGES, true);
            jobContext.put(CTX_AFFECTED_BBOXES, bboxes);
            jobContext.put(CTX_LAYER_NAME, metadata.layerName());
        }
    }

    private List<String> compareStreamingSource(JdbcTemplate sourceJdbc,
                                                LayerTableMetadata metadata,
                                                Map<Object, RecordComparison> target) {
        String pk = metadata.primaryKeyColumn();
        String geom = metadata.geometryColumn();
        String table = metadata.qualifiedSourceTable();
        String where = metadata.whereClause();
        List<String> comparisonColumns = metadata.sourceComparisonColumnNames();
        String attributeColumns = joinAttributeSelectColumns(pk, comparisonColumns);

        String sql = buildComparisonSql(metadata, attributeColumns, geom, table, "WHERE " + where);

        List<String> bboxes = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        AtomicInteger sourceCount = new AtomicInteger();

        sourceJdbc.query(sql, rs -> {
            sourceCount.incrementAndGet();

            Object id = normalizeId(rs.getObject(pk));
            String hash = calculateHashWithGeometry(rs, comparisonColumns);
            String bbox = formatBbox(rs.getDouble("minx"), rs.getDouble("miny"),
                    rs.getDouble("maxx"), rs.getDouble("maxy"));

            RecordComparison targetRec = target.get(id);

            if (targetRec == null) {
                log.debug("New record: {}", id);
                bboxes.add(bbox);
            } else if (!hash.equals(targetRec.hash)) {
                modified.add(String.format("id=%s | SOURCE: %s | TARGET: %s",
                        id, hash, targetRec.hash));
                bboxes.add(mergeBbox(bbox, targetRec.bbox));
            }

            target.remove(id);
        });

        log.info("Source: {} records processed", sourceCount.get());

        if (!modified.isEmpty()) {
            log.warn("MODIFIED RECORDS: {}", modified.size());
            for (String mod : modified) {
                log.warn("MODIFIED: {}", mod);
            }
        }

        if (!target.isEmpty()) {
            log.warn("DELETED RECORDS: {}", target.size());
            for (RecordComparison reg : target.values()) {
                log.warn("DELETED: id={} | HASH: {}", reg.id, reg.hash);
                bboxes.add(reg.bbox);
            }
        }

        return bboxes;
    }

    private Map<Object, RecordComparison> fetchTargetData(JdbcTemplate targetJdbc,
                                                          LayerTableMetadata metadata) {
        String pk = metadata.resolveTargetPrimaryKeyColumn();
        String geom = metadata.geometryColumn();
        String table = metadata.qualifiedTargetTable();
        List<String> targetColumns = metadata.targetComparisonColumnNames();
        String attributeColumns = joinAttributeSelectColumns(pk, targetColumns);

        String sql = buildComparisonSql(metadata, attributeColumns, geom, table, "");

        return targetJdbc.query(sql, rs -> {
            Map<Object, RecordComparison> map = new HashMap<>();
            while (rs.next()) {
                Object id = normalizeId(rs.getObject(pk));
                String hash = calculateHashWithGeometry(rs, targetColumns);
                String bbox = formatBbox(rs.getDouble("minx"), rs.getDouble("miny"),
                        rs.getDouble("maxx"), rs.getDouble("maxy"));
                map.put(id, new RecordComparison(id, hash, bbox));
            }
            return map;
        });
    }

    String joinAttributeSelectColumns(String primaryKey, List<String> columnNames) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        unique.add(primaryKey);
        for (String column : columnNames) {
            if (!column.equals(primaryKey)) {
                unique.add(column);
            }
        }
        return String.join(", ", unique);
    }

    String buildComparisonSql(LayerTableMetadata metadata,
                              String attributeColumns,
                              String geom,
                              String table,
                              String whereClause) {
        int srid = metadata.srid();

        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);
        String finalWhere = whereClause.isEmpty()
                ? "WHERE " + validGeomFilter
                : whereClause + " AND " + validGeomFilter;

        // Metrics / envelope in 2D so Point Z (etc.) matches geo-target exhibition columns.
        String geom2d = GeometrySql.force2d(geom);

        return String.format(
                "SELECT %s, "
                        + "ST_XMin(env3857) as minx, "
                        + "ST_YMin(env3857) as miny, "
                        + "ST_XMax(env3857) as maxx, "
                        + "ST_YMax(env3857) as maxy, "
                        + "ROUND(ST_Area(%s)::numeric, 5) as area, "
                        + "ROUND(ST_X(ST_Centroid(%s))::numeric, 5) as centroid_x, "
                        + "ROUND(ST_Y(ST_Centroid(%s))::numeric, 5) as centroid_y "
                        + "FROM ("
                        + "  SELECT %s, %s AS %s, "
                        + "  ST_Transform(ST_Envelope(ST_SetSRID(%s, %d)), 3857) as env3857 "
                        + "  FROM %s %s"
                        + ") t",
                attributeColumns,
                LAYER_GEOM_2D_ALIAS,
                LAYER_GEOM_2D_ALIAS,
                LAYER_GEOM_2D_ALIAS,
                attributeColumns,
                geom2d,
                LAYER_GEOM_2D_ALIAS,
                geom2d,
                srid,
                table,
                finalWhere
        );
    }

    private String calculateHashWithGeometry(ResultSet rs, List<String> columns) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (String col : columns) {
            Object value = rs.getObject(col);
            sb.append(value != null ? value.toString() : "null").append("|");
        }
        BigDecimal area = rs.getBigDecimal("area");
        sb.append(area != null ? area : "0.00").append("|");

        BigDecimal centroidX = rs.getBigDecimal("centroid_x");
        BigDecimal centroidY = rs.getBigDecimal("centroid_y");
        sb.append(centroidX != null ? centroidX : "0.0").append(",");
        sb.append(centroidY != null ? centroidY : "0.0");

        return sb.toString();
    }

    private String formatBbox(double minX, double minY, double maxX, double maxY) {
        return String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", minX, minY, maxX, maxY);
    }

    private String mergeBbox(String bbox1, String bbox2) {
        String[] a = bbox1.split(",");
        String[] b = bbox2.split(",");
        return formatBbox(
                Math.min(Double.parseDouble(a[0]), Double.parseDouble(b[0])),
                Math.min(Double.parseDouble(a[1]), Double.parseDouble(b[1])),
                Math.max(Double.parseDouble(a[2]), Double.parseDouble(b[2])),
                Math.max(Double.parseDouble(a[3]), Double.parseDouble(b[3]))
        );
    }

    private void deleteRemovedRecords(JdbcTemplate targetJdbc,
                                      LayerTableMetadata metadata,
                                      Set<Object> idsToDelete) {
        if (idsToDelete.isEmpty()) {
            return;
        }

        String pk = metadata.resolveTargetPrimaryKeyColumn();
        String table = metadata.qualifiedTargetTable();

        String placeholders = idsToDelete.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = String.format("DELETE FROM %s WHERE %s IN (%s)", table, pk, placeholders);

        int deleted = targetJdbc.update(sql, idsToDelete.toArray());
        log.warn("Deleted {} inactive records from target: {}", deleted, idsToDelete);
    }

    Object normalizeId(Object id) {
        if (id == null) {
            return null;
        }
        if (id instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        return id.toString().trim();
    }

    private record RecordComparison(Object id, String hash, String bbox) {
    }
}
