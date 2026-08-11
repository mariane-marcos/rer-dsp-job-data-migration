package br.car.dsp_batch.layer.service;

import br.car.dsp_batch.geometry.GeometrySql;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.sync.LayerSyncState;
import br.car.dsp_batch.layer.sync.LayerSyncStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Incremental change detection for layers using an {@code updated_at} watermark.
 * Orphan (deleted) rows are checked on the initial load and periodically afterwards.
 */
@Slf4j
@Service
public class LayerChangeDetectionService {

    public static final String CTX_HAS_CHANGES = "hasChanges";
    public static final String CTX_AFFECTED_BBOXES = "affectedBboxes";
    public static final String CTX_LAYER_NAME = "layerName";
    public static final String CTX_PROPOSED_WATERMARK = "proposedWatermark";
    public static final String CTX_ORPHAN_CHECK_RAN = "orphanCheckRan";

    /** Maximum interval between orphan scans. */
    static final Duration ORPHAN_CHECK_INTERVAL = Duration.ofHours(24);

    private final LayerSyncStateRepository syncStateRepository;

    public LayerChangeDetectionService(LayerSyncStateRepository syncStateRepository) {
        this.syncStateRepository = syncStateRepository;
    }

    public void detectChanges(JdbcTemplate sourceJdbc,
                              JdbcTemplate geoTargetJdbc,
                              LayerTableMetadata metadata,
                              ChunkContext chunkContext) {
        LayerFeaturePersistenceService.requirePositiveSrid(metadata);
        log.info("Starting incremental change detection for table: {}", metadata.qualifiedSourceTable());

        Optional<LayerSyncState> state = syncStateRepository.findByLayerKey(metadata.layerKey());
        Instant watermark = state.map(LayerSyncState::watermarkUpdatedAt).orElse(null);
        boolean runOrphanCheck = shouldRunOrphanCheck(state.orElse(null));

        List<String> deltaBboxes = new ArrayList<>();
        Instant maxUpdatedAt = collectDeltaBboxes(sourceJdbc, metadata, watermark, deltaBboxes);

        List<String> affectedBboxes = new ArrayList<>(deltaBboxes);
        if (runOrphanCheck) {
            log.info("Running orphan check for {}", metadata.qualifiedSourceTable());
            List<String> orphanBboxes = deleteOrphans(sourceJdbc, geoTargetJdbc, metadata);
            affectedBboxes.addAll(orphanBboxes);
        }

        var jobContext = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        jobContext.put(CTX_ORPHAN_CHECK_RAN, runOrphanCheck);

        if (maxUpdatedAt != null) {
            jobContext.putString(CTX_PROPOSED_WATERMARK, maxUpdatedAt.toString());
        }

        // PROCESS (UPSERT) only when there is a delta; orphans were already deleted above.
        boolean hasDelta = !deltaBboxes.isEmpty();
        if (!hasDelta && affectedBboxes.isEmpty()) {
            log.info("No changes detected in {} (watermark={})",
                    metadata.qualifiedSourceTable(), watermark);
            jobContext.put(CTX_HAS_CHANGES, false);
        } else if (!hasDelta) {
            log.info("Only orphan deletions in {} — skipping UPSERT (watermark={})",
                    metadata.qualifiedSourceTable(), watermark);
            jobContext.put(CTX_HAS_CHANGES, false);
            jobContext.put(CTX_AFFECTED_BBOXES, affectedBboxes);
            jobContext.put(CTX_LAYER_NAME, metadata.layerName());
        } else {
            log.info("Detected {} delta areas in {} (watermark={}, proposed={})",
                    deltaBboxes.size(), metadata.qualifiedSourceTable(), watermark, maxUpdatedAt);
            jobContext.put(CTX_HAS_CHANGES, true);
            jobContext.put(CTX_AFFECTED_BBOXES, affectedBboxes);
            jobContext.put(CTX_LAYER_NAME, metadata.layerName());
        }
    }

    boolean shouldRunOrphanCheck(LayerSyncState state) {
        if (state == null || state.watermarkUpdatedAt() == null) {
            return true;
        }
        Instant lastCheck = state.lastOrphanCheckAt();
        if (lastCheck == null) {
            return true;
        }
        return lastCheck.isBefore(Instant.now().minus(ORPHAN_CHECK_INTERVAL));
    }

    /**
     * Collects delta bboxes and returns the {@code MAX(updated_at)} found (or null).
     */
    Instant collectDeltaBboxes(JdbcTemplate sourceJdbc,
                               LayerTableMetadata metadata,
                               Instant watermark,
                               List<String> bboxes) {
        String pk = metadata.primaryKeyColumn();
        String geom = metadata.geometryColumn();
        String updatedAt = metadata.updatedAtSourceColumn();
        String table = metadata.qualifiedSourceTable();
        int srid = metadata.srid();

        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);
        String configWhere = metadata.whereClause();
        boolean hasConfigWhere = configWhere != null
                && !configWhere.isBlank()
                && !"1=1".equals(configWhere.trim());

        StringBuilder where = new StringBuilder("WHERE ").append(validGeomFilter);
        where.append(" AND ").append(updatedAt).append(" IS NOT NULL");
        if (hasConfigWhere) {
            where.append(" AND (").append(configWhere).append(")");
        }
        List<Object> params = new ArrayList<>();
        if (watermark != null) {
            where.append(" AND ").append(updatedAt).append(" > ?");
            params.add(Timestamp.from(watermark));
        }

        String geom2d = GeometrySql.force2d(geom);
        String sql = String.format(
                "SELECT %s, feature_updated_at, "
                        + "ST_XMin(env3857) as minx, "
                        + "ST_YMin(env3857) as miny, "
                        + "ST_XMax(env3857) as maxx, "
                        + "ST_YMax(env3857) as maxy "
                        + "FROM ("
                        + "  SELECT %s, %s AS feature_updated_at, "
                        + "  ST_Transform(ST_Envelope(ST_SetSRID(%s, %d)), 3857) as env3857 "
                        + "  FROM %s %s"
                        + ") t",
                pk,
                pk,
                updatedAt,
                geom2d,
                srid,
                table,
                where
        );

        AtomicReference<Instant> maxUpdatedAt = new AtomicReference<>();
        sourceJdbc.query(sql, rs -> {
            Timestamp ts = rs.getTimestamp("feature_updated_at");
            if (ts != null) {
                Instant instant = ts.toInstant();
                maxUpdatedAt.updateAndGet(current ->
                        current == null || instant.isAfter(current) ? instant : current);
            }
            bboxes.add(formatBbox(
                    rs.getDouble("minx"),
                    rs.getDouble("miny"),
                    rs.getDouble("maxx"),
                    rs.getDouble("maxy")));
        }, params.toArray());

        log.info("Source delta: {} record(s) after watermark {}", bboxes.size(), watermark);
        return maxUpdatedAt.get();
    }

    private List<String> deleteOrphans(JdbcTemplate sourceJdbc,
                                       JdbcTemplate geoTargetJdbc,
                                       LayerTableMetadata metadata) {
        Set<Object> sourceIds = fetchSourceIds(sourceJdbc, metadata);
        MapIdsAndBboxes target = fetchTargetIdsAndBboxes(geoTargetJdbc, metadata);

        Set<Object> orphans = new HashSet<>(target.ids());
        orphans.removeAll(sourceIds);

        if (orphans.isEmpty()) {
            log.info("Orphan check: no deleted records in {}", metadata.qualifiedTargetTable());
            return List.of();
        }

        List<String> orphanBboxes = new ArrayList<>();
        for (Object id : orphans) {
            String bbox = target.bboxesById().get(id);
            if (bbox != null) {
                orphanBboxes.add(bbox);
            }
            log.warn("DELETED orphan: id={}", id);
        }

        deleteRemovedRecords(geoTargetJdbc, metadata, orphans);
        return orphanBboxes;
    }

    private Set<Object> fetchSourceIds(JdbcTemplate sourceJdbc, LayerTableMetadata metadata) {
        String pk = metadata.primaryKeyColumn();
        String geom = metadata.geometryColumn();
        String table = metadata.qualifiedSourceTable();
        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);
        String configWhere = metadata.whereClause();
        boolean hasConfigWhere = configWhere != null
                && !configWhere.isBlank()
                && !"1=1".equals(configWhere.trim());

        StringBuilder sql = new StringBuilder("SELECT ").append(pk)
                .append(" FROM ").append(table)
                .append(" WHERE ").append(validGeomFilter);
        if (hasConfigWhere) {
            sql.append(" AND (").append(configWhere).append(")");
        }

        Set<Object> ids = new HashSet<>();
        sourceJdbc.query(sql.toString(), rs -> {
            ids.add(normalizeId(rs.getObject(pk)));
        });
        log.info("Orphan check source ids: {}", ids.size());
        return ids;
    }

    private MapIdsAndBboxes fetchTargetIdsAndBboxes(JdbcTemplate targetJdbc,
                                                    LayerTableMetadata metadata) {
        String pk = metadata.resolveTargetPrimaryKeyColumn();
        String geom = metadata.resolveTargetGeometryColumn();
        String table = metadata.qualifiedTargetTable();
        int srid = metadata.srid();
        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);
        String geom2d = GeometrySql.force2d(geom);

        String sql = String.format(
                "SELECT %s, "
                        + "ST_XMin(env3857) as minx, "
                        + "ST_YMin(env3857) as miny, "
                        + "ST_XMax(env3857) as maxx, "
                        + "ST_YMax(env3857) as maxy "
                        + "FROM ("
                        + "  SELECT %s, "
                        + "  ST_Transform(ST_Envelope(ST_SetSRID(%s, %d)), 3857) as env3857 "
                        + "  FROM %s WHERE %s"
                        + ") t",
                pk, pk, geom2d, srid, table, validGeomFilter
        );

        Set<Object> ids = new HashSet<>();
        java.util.Map<Object, String> bboxesById = new java.util.HashMap<>();
        targetJdbc.query(sql, rs -> {
            Object id = normalizeId(rs.getObject(pk));
            ids.add(id);
            bboxesById.put(id, formatBbox(
                    rs.getDouble("minx"),
                    rs.getDouble("miny"),
                    rs.getDouble("maxx"),
                    rs.getDouble("maxy")));
        });
        log.info("Orphan check target ids: {}", ids.size());
        return new MapIdsAndBboxes(ids, bboxesById);
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
        log.warn("Deleted {} inactive records from target: {}", deleted, idsToDelete.size());
    }

    /**
     * SQL fragment that filters by the source update column (used by reader/partitioner).
     * Always requires {@code IS NOT NULL}. With a watermark, restricts to the delta.
     */
    public static String buildUpdatedAtFilterSql(String updatedAtSourceColumn, Instant watermark) {
        String notNull = updatedAtSourceColumn + " IS NOT NULL";
        if (watermark == null) {
            return notNull;
        }
        return notNull + " AND " + updatedAtSourceColumn + " > TIMESTAMP WITH TIME ZONE '"
                + watermark.toString() + "'";
    }

    private String formatBbox(double minX, double minY, double maxX, double maxY) {
        return String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", minX, minY, maxX, maxY);
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

    private record MapIdsAndBboxes(Set<Object> ids, java.util.Map<Object, String> bboxesById) {
    }
}
