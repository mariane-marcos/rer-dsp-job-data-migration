package br.car.dsp_batch.layer.sync;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Persistence for layer watermarks on the primary datasource ({@code batch_metadata}).
 */
@Repository
public class LayerSyncStateRepository {

    private static final RowMapper<LayerSyncState> ROW_MAPPER = (rs, rowNum) -> new LayerSyncState(
            rs.getString("layer_key"),
            rs.getString("source_table"),
            toInstant(rs.getTimestamp("watermark_updated_at")),
            toInstant(rs.getTimestamp("last_success_at")),
            (Long) rs.getObject("last_job_execution_id"),
            toInstant(rs.getTimestamp("last_orphan_check_at"))
    );

    private final JdbcTemplate batchJdbcTemplate;

    public LayerSyncStateRepository(@Qualifier("jdbcTemplate") JdbcTemplate batchJdbcTemplate) {
        this.batchJdbcTemplate = batchJdbcTemplate;
    }

    public Optional<LayerSyncState> findByLayerKey(String layerKey) {
        try {
            LayerSyncState state = batchJdbcTemplate.queryForObject(
                    """
                    SELECT layer_key, source_table, watermark_updated_at, last_success_at,
                           last_job_execution_id, last_orphan_check_at
                    FROM dsp_layer_sync_state
                    WHERE layer_key = ?
                    """,
                    ROW_MAPPER,
                    layerKey
            );
            return Optional.ofNullable(state);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<Instant> findWatermark(String layerKey) {
        return findByLayerKey(layerKey).map(LayerSyncState::watermarkUpdatedAt);
    }

    /**
     * Advances the watermark after a successful run.
     * If {@code watermarkUpdatedAt} is null, keeps the current value.
     */
    public void advanceWatermark(String layerKey,
                                 String sourceTable,
                                 Instant watermarkUpdatedAt,
                                 long jobExecutionId) {
        Instant now = Instant.now();
        batchJdbcTemplate.update(
                """
                INSERT INTO dsp_layer_sync_state (
                    layer_key, source_table, watermark_updated_at, last_success_at,
                    last_job_execution_id, last_orphan_check_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, NULL, ?)
                ON CONFLICT (layer_key) DO UPDATE SET
                    source_table = EXCLUDED.source_table,
                    watermark_updated_at = COALESCE(EXCLUDED.watermark_updated_at,
                        dsp_layer_sync_state.watermark_updated_at),
                    last_success_at = EXCLUDED.last_success_at,
                    last_job_execution_id = EXCLUDED.last_job_execution_id,
                    updated_at = EXCLUDED.updated_at
                """,
                layerKey,
                sourceTable,
                toTimestamp(watermarkUpdatedAt),
                toTimestamp(now),
                jobExecutionId,
                toTimestamp(now)
        );
    }

    public void markOrphanCheck(String layerKey, String sourceTable) {
        Instant now = Instant.now();
        batchJdbcTemplate.update(
                """
                INSERT INTO dsp_layer_sync_state (
                    layer_key, source_table, watermark_updated_at, last_success_at,
                    last_job_execution_id, last_orphan_check_at, updated_at
                ) VALUES (?, ?, NULL, NULL, NULL, ?, ?)
                ON CONFLICT (layer_key) DO UPDATE SET
                    source_table = EXCLUDED.source_table,
                    last_orphan_check_at = EXCLUDED.last_orphan_check_at,
                    updated_at = EXCLUDED.updated_at
                """,
                layerKey,
                sourceTable,
                toTimestamp(now),
                toTimestamp(now)
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
