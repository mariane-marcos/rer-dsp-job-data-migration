package br.car.dsp_batch.batch.config.strategy;

import br.car.dsp_batch.batch.config.JobTableConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * DEFAULT change detection strategy.
 * Compares source and target records by attribute hash and geometry metrics,
 * collects affected bounding boxes, and deletes target orphans.
 */
@Slf4j
@Component
public class DefaultChangeDetectionStrategy implements ChangeDetectionStrategy {

    public static final String CTX_HAS_CHANGES = "hasChanges";
    public static final String CTX_AFFECTED_BBOXES = "affectedBboxes";
    public static final String CTX_LAYER_NAME = "layerName";

    @Override
    public ChangeDetectionStrategyType getType() {
        return ChangeDetectionStrategyType.DEFAULT;
    }

    @Override
    public void detectChanges(JdbcTemplate sourceJdbc,
                              JdbcTemplate targetJdbc,
                              JobTableConfig tableConfig,
                              ChunkContext chunkContext) {
        log.info("Starting change detection for table: {}", tableConfig.getSourceTable());

        Map<Object, RecordComparison> target = fetchTargetData(targetJdbc, tableConfig);
        log.info("Target: {} records", target.size());

        List<String> bboxes = compareStreamingSource(sourceJdbc, tableConfig, target);

        if (!target.isEmpty()) {
            deleteRemovedRecords(targetJdbc, tableConfig, target.keySet());
        }

        var jobContext = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        if (bboxes.isEmpty()) {
            log.info("No changes detected in {}", tableConfig.getSourceTable());
            jobContext.put(CTX_HAS_CHANGES, false);
        } else {
            log.info("Detected {} areas with changes in {}",
                    bboxes.size(), tableConfig.getSourceTable());
            jobContext.put(CTX_HAS_CHANGES, true);
            jobContext.put(CTX_AFFECTED_BBOXES, bboxes);
            jobContext.put(CTX_LAYER_NAME, tableConfig.getLayerName());
        }
    }

    private List<String> compareStreamingSource(JdbcTemplate sourceJdbc,
                                                JobTableConfig tableConfig,
                                                Map<Object, RecordComparison> target) {
        String pk = tableConfig.getPrimaryKey();
        String geom = tableConfig.getGeometryColumn();
        String table = tableConfig.getSourceTable();
        String where = tableConfig.getWhereClause();
        String columns = String.join(", ", tableConfig.getComparisonColumns());

        String sql = buildComparisonSql(tableConfig, pk, columns, geom, table, "WHERE " + where);

        List<String> bboxes = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        AtomicInteger sourceCount = new AtomicInteger();

        sourceJdbc.query(sql, rs -> {
            sourceCount.incrementAndGet();

            Object id = normalizeId(rs.getObject(pk));
            String hash = calculateHashWithGeometry(rs, tableConfig.getComparisonColumns());
            String bbox = formatBbox(rs.getDouble("minx"), rs.getDouble("miny"),
                    rs.getDouble("maxx"), rs.getDouble("maxy"));

            RecordComparison targetRec = target.get(id);

            if (targetRec == null) {
                log.debug("New record: {}", id);
                bboxes.add(bbox);
            } else if (!hash.equals(targetRec.hash)) {
                modified.add(String.format("id=%s | SOURCE: %s | TARGET: %s", id, hash, targetRec.hash));
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
                                                          JobTableConfig tableConfig) {
        String pk = tableConfig.resolveTargetColumn(tableConfig.getPrimaryKey());
        String geom = tableConfig.resolveTargetColumn(tableConfig.getGeometryColumn());
        String table = tableConfig.getTargetTable();

        List<String> targetColumns = tableConfig.getComparisonColumns().stream()
                .map(tableConfig::resolveTargetColumn)
                .collect(Collectors.toList());
        String columns = String.join(", ", targetColumns);

        String sql = buildComparisonSql(tableConfig, pk, columns, geom, table, "");

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

    private String buildComparisonSql(JobTableConfig tableConfig,
                                      String pk,
                                      String columns,
                                      String geom,
                                      String table,
                                      String whereClause) {
        int srid = tableConfig.getSrid();

        String validGeomFilter = String.format(
                "(%s IS NOT NULL"
                        + " AND NOT ST_IsEmpty(ST_Multi(ST_CollectionExtract(ST_MakeValid(COALESCE(%s, ST_Buffer(%s, 0))), 3))))",
                geom, geom, geom
        );
        String finalWhere = whereClause.isEmpty()
                ? "WHERE " + validGeomFilter
                : whereClause + " AND " + validGeomFilter;

        return String.format(
                "SELECT %s, %s, "
                        + "ST_XMin(env3857) as minx, "
                        + "ST_YMin(env3857) as miny, "
                        + "ST_XMax(env3857) as maxx, "
                        + "ST_YMax(env3857) as maxy, "
                        + "ROUND(ST_Area(%s)::numeric, 5) as area, "
                        + "ROUND(ST_X(ST_Centroid(%s))::numeric, 5) as centroid_x, "
                        + "ROUND(ST_Y(ST_Centroid(%s))::numeric, 5) as centroid_y "
                        + "FROM ("
                        + "  SELECT %s, %s, %s, "
                        + "  ST_Transform(ST_Envelope(ST_SetSRID(%s, %d)), 3857) as env3857 "
                        + "  FROM %s %s"
                        + ") t",
                pk, columns, geom, geom, geom, pk, columns, geom, geom, srid, table, finalWhere
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
                                      JobTableConfig tableConfig,
                                      Set<Object> idsToDelete) {
        if (idsToDelete.isEmpty()) {
            return;
        }

        String pk = tableConfig.resolveTargetColumn(tableConfig.getPrimaryKey());
        String table = tableConfig.getTargetTable();

        String placeholders = idsToDelete.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = String.format("DELETE FROM %s WHERE %s IN (%s)", table, pk, placeholders);

        int deleted = targetJdbc.update(sql, idsToDelete.toArray());
        log.warn("Deleted {} inactive records from target: {}", deleted, idsToDelete);
    }

    private Object normalizeId(Object id) {
        if (id instanceof Number) {
            return ((Number) id).longValue();
        }
        return id;
    }

    private record RecordComparison(Object id, String hash, String bbox) {
    }
}
