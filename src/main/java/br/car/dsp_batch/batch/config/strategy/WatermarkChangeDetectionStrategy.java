package br.car.dsp_batch.batch.config.strategy;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.service.AdministrativeUnitPersistenceService;
import br.car.dsp_batch.sync.WatermarkChangeDetectionEngine;
import br.car.dsp_batch.sync.WatermarkTableSpecs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Incremental change detection using an {@code updated-at-column} watermark.
 * Delegates the shared algorithm to {@link WatermarkChangeDetectionEngine}.
 */
@Slf4j
@Component
public class WatermarkChangeDetectionStrategy implements ChangeDetectionStrategy {

    private final WatermarkChangeDetectionEngine engine;

    public WatermarkChangeDetectionStrategy(WatermarkChangeDetectionEngine engine) {
        this.engine = engine;
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
        requireSyncKey(tableConfig);

        engine.detectChanges(
                sourceJdbc,
                geoTargetJdbc,
                targetJdbc,
                WatermarkTableSpecs.fromJobTableConfig(tableConfig),
                chunkContext
        );
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
}
