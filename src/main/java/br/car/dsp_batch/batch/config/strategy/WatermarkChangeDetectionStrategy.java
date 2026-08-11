package br.car.dsp_batch.batch.config.strategy;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.geometry.GeometrySql;
import br.car.dsp_batch.service.AdministrativeUnitPersistenceService;
import br.car.dsp_batch.sync.SyncState;
import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.sync.WatermarkContextKeys;
import br.car.dsp_batch.sync.WatermarkSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Incremental change detection using an {@code updated-at-column} watermark.
 * Orphan rows are removed from both business and geo targets on a periodic schedule.
 */
@Slf4j
@Component
public class WatermarkChangeDetectionStrategy implements ChangeDetectionStrategy {

    private final SyncStateRepository syncStateRepository;

    public WatermarkChangeDetectionStrategy(SyncStateRepository syncStateRepository) {
        this.syncStateRepository = syncStateRepository;
    }

    @Override
    public ChangeDetectionStrategyType getType() {
        return ChangeDetectionStrategyType.WATERMARK;
    }

    @Override
    public void detectChanges(JdbcTemplate sourceJdbc,
                              JdbcTemplate targetJdbc,
                              JdbcTemplate geoTargetJdbc,
                              JobTableConfig tableConfig,
                              ChunkContext chunkContext) {
        AdministrativeUnitPersistenceService.requirePositiveSrid(tableConfig);
        requireUpdatedAtColumn(tableConfig);

        String syncKey = requireSyncKey(tableConfig);
        log.info("Starting watermark change detection for table={} syncKey={}",
                tableConfig.getSourceTable(), syncKey);

        Optional<SyncState> state = syncStateRepository.findBySyncKey(syncKey);
        Instant watermark = state.map(SyncState::watermarkUpdatedAt).orElse(null);
        boolean runOrphanCheck = shouldRunOrphanCheck(state.orElse(null));

        List<String> deltaBboxes = new ArrayList<>();
        Instant maxUpdatedAt = collectDeltaBboxes(sourceJdbc, tableConfig, watermark, deltaBboxes);

        List<String> affectedBboxes = new ArrayList<>(deltaBboxes);
        if (runOrphanCheck) {
            log.info("Running orphan check for {}", tableConfig.getSourceTable());
            affectedBboxes.addAll(deleteOrphans(sourceJdbc, targetJdbc, geoTargetJdbc, tableConfig));
        }

        var jobContext = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        jobContext.putString(WatermarkContextKeys.SYNC_KEY, syncKey);
        jobContext.putString(WatermarkContextKeys.SOURCE_TABLE, tableConfig.getSourceTable());
        jobContext.put(WatermarkContextKeys.ORPHAN_CHECK_RAN, runOrphanCheck);

        if (maxUpdatedAt != null) {
            jobContext.putString(WatermarkContextKeys.PROPOSED_WATERMARK, maxUpdatedAt.toString());
        }

        boolean hasDelta = !deltaBboxes.isEmpty();
        if (!hasDelta && affectedBboxes.isEmpty()) {
            log.info("No changes detected in {} (watermark={})",
                    tableConfig.getSourceTable(), watermark);
            jobContext.put(WatermarkContextKeys.HAS_CHANGES, false);
        } else if (!hasDelta) {
            log.info("Only orphan deletions in {} — skipping UPSERT (watermark={})",
                    tableConfig.getSourceTable(), watermark);
            jobContext.put(WatermarkContextKeys.HAS_CHANGES, false);
            jobContext.put(WatermarkContextKeys.AFFECTED_BBOXES, affectedBboxes);
            jobContext.put(WatermarkContextKeys.LAYER_NAME, tableConfig.getLayerName());
        } else {
            log.info("Detected {} delta areas in {} (watermark={}, proposed={})",
                    deltaBboxes.size(), tableConfig.getSourceTable(), watermark, maxUpdatedAt);
            jobContext.put(WatermarkContextKeys.HAS_CHANGES, true);
            jobContext.put(WatermarkContextKeys.AFFECTED_BBOXES, affectedBboxes);
            jobContext.put(WatermarkContextKeys.LAYER_NAME, tableConfig.getLayerName());
        }
    }

    static void requireUpdatedAtColumn(JobTableConfig tableConfig) {
        String column = tableConfig.getUpdatedAtColumn();
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException(
                    "WATERMARK strategy requires updated-at-column for table "
                            + tableConfig.getSourceTable());
        }
    }

    static String requireSyncKey(JobTableConfig tableConfig) {
        String syncKey = tableConfig.getSyncKey();
        if (syncKey == null || syncKey.isBlank()) {
            throw new IllegalArgumentException(
                    "WATERMARK strategy requires sync-key for table "
                            + tableConfig.getSourceTable());
        }
        return syncKey.trim();
    }

    boolean shouldRunOrphanCheck(SyncState state) {
        if (state == null || state.watermarkUpdatedAt() == null) {
            return true;
        }
        Instant lastCheck = state.lastOrphanCheckAt();
        if (lastCheck == null) {
            return true;
        }
        return lastCheck.isBefore(Instant.now().minus(WatermarkSettings.ORPHAN_CHECK_INTERVAL));
    }

    Instant collectDeltaBboxes(JdbcTemplate sourceJdbc,
                               JobTableConfig tableConfig,
                               Instant watermark,
                               List<String> bboxes) {
        String pk = tableConfig.getPrimaryKey();
        String geom = tableConfig.getGeometryColumn();
        String updatedAt = tableConfig.getUpdatedAtColumn().trim();
        String table = tableConfig.getSourceTable();
        int srid = tableConfig.getSrid();

        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);
        String configWhere = tableConfig.getWhereClause();
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
                                       JdbcTemplate targetJdbc,
                                       JdbcTemplate geoTargetJdbc,
                                       JobTableConfig tableConfig) {
        Set<Object> sourceIds = fetchSourceIds(sourceJdbc, tableConfig);
        TargetIdsAndBboxes geoTarget = fetchTargetIdsAndBboxes(geoTargetJdbc, tableConfig);

        Set<Object> orphans = new HashSet<>(geoTarget.ids());
        orphans.removeAll(sourceIds);

        if (orphans.isEmpty()) {
            log.info("Orphan check: no deleted records for {}", tableConfig.getTargetTable());
            return List.of();
        }

        List<String> orphanBboxes = new ArrayList<>();
        for (Object id : orphans) {
            String bbox = geoTarget.bboxesById().get(id);
            if (bbox != null) {
                orphanBboxes.add(bbox);
            }
            log.warn("DELETED orphan: id={}", id);
        }

        deleteRemovedRecords(targetJdbc, tableConfig, orphans);
        deleteRemovedRecords(geoTargetJdbc, tableConfig, orphans);
        return orphanBboxes;
    }

    private Set<Object> fetchSourceIds(JdbcTemplate sourceJdbc, JobTableConfig tableConfig) {
        String pk = tableConfig.getPrimaryKey();
        String geom = tableConfig.getGeometryColumn();
        String table = tableConfig.getSourceTable();
        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);
        String configWhere = tableConfig.getWhereClause();
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
        RowCallbackHandler handler = rs -> ids.add(normalizeId(rs.getObject(pk)));
        sourceJdbc.query(sql.toString(), handler);
        log.info("Orphan check source ids: {}", ids.size());
        return ids;
    }

    private TargetIdsAndBboxes fetchTargetIdsAndBboxes(JdbcTemplate targetJdbc,
                                                       JobTableConfig tableConfig) {
        String pk = tableConfig.resolveTargetColumn(tableConfig.getPrimaryKey());
        String geom = tableConfig.resolveTargetColumn(tableConfig.getGeometryColumn());
        String table = tableConfig.getTargetTable();
        int srid = tableConfig.getSrid();
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
        Map<Object, String> bboxesById = new java.util.HashMap<>();
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
        return new TargetIdsAndBboxes(ids, bboxesById);
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
        log.warn("Deleted {} inactive records from {}: {}", deleted, table, idsToDelete.size());
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

    private record TargetIdsAndBboxes(Set<Object> ids, Map<Object, String> bboxesById) {
    }
}
