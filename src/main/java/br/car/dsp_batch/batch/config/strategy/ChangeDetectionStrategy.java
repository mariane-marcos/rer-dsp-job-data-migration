package br.car.dsp_batch.batch.config.strategy;

import br.car.dsp_batch.batch.config.JobTableConfig;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Strategy for detecting changes between source and target geographic tables.
 */
public interface ChangeDetectionStrategy {

    ChangeDetectionStrategyType getType();

    /**
     * Detects changes and writes results into the job execution context
     * ({@code hasChanges}, {@code affectedBboxes}, {@code layerName}).
     */
    void detectChanges(JdbcTemplate sourceJdbc,
                       JdbcTemplate targetJdbc,
                       JobTableConfig tableConfig,
                       ChunkContext chunkContext);
}
