package br.car.dsp_batch.layer.service;

import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.sync.SyncState;
import br.car.dsp_batch.sync.WatermarkChangeDetectionEngine;
import br.car.dsp_batch.sync.WatermarkContextKeys;
import br.car.dsp_batch.sync.WatermarkSettings;
import br.car.dsp_batch.sync.WatermarkSql;
import br.car.dsp_batch.sync.WatermarkTableSpecs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Incremental change detection for layers using a watermark.
 * Delegates the shared algorithm to {@link WatermarkChangeDetectionEngine}.
 */
@Slf4j
@Service
public class LayerChangeDetectionService {

    public static final String CTX_HAS_CHANGES = WatermarkContextKeys.HAS_CHANGES;
    public static final String CTX_AFFECTED_BBOXES = WatermarkContextKeys.AFFECTED_BBOXES;
    public static final String CTX_LAYER_NAME = WatermarkContextKeys.LAYER_NAME;
    public static final String CTX_PROPOSED_WATERMARK = WatermarkContextKeys.PROPOSED_WATERMARK;
    public static final String CTX_ORPHAN_CHECK_RAN = WatermarkContextKeys.ORPHAN_CHECK_RAN;

    /** Maximum interval between orphan scans. */
    static final Duration ORPHAN_CHECK_INTERVAL = WatermarkSettings.ORPHAN_CHECK_INTERVAL;

    private final WatermarkChangeDetectionEngine engine;

    public LayerChangeDetectionService(WatermarkChangeDetectionEngine engine) {
        this.engine = engine;
    }

    public void detectChanges(JdbcTemplate sourceJdbc,
                              JdbcTemplate geoTargetJdbc,
                              LayerTableMetadata metadata,
                              ChunkContext chunkContext) {
        LayerFeaturePersistenceService.requirePositiveSrid(metadata);
        engine.detectChanges(
                sourceJdbc,
                geoTargetJdbc,
                null,
                WatermarkTableSpecs.fromLayerMetadata(metadata),
                chunkContext
        );
    }

    boolean shouldRunOrphanCheck(SyncState state) {
        return engine.shouldRunOrphanCheck(state);
    }

    /**
     * SQL fragment that filters by the source update column (used by reader/partitioner).
     */
    public static String buildUpdatedAtFilterSql(String updatedAtSourceColumn, Instant watermark) {
        return WatermarkSql.buildUpdatedAtFilter(updatedAtSourceColumn, watermark);
    }

    Object normalizeId(Object id) {
        return engine.normalizeId(id);
    }
}
