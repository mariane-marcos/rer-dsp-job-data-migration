package br.car.dsp_batch.batch.tasklet;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tasklet that delegates change detection to a {@link ChangeDetectionStrategy}.
 */
@Slf4j
public class ChangeDetectionTasklet implements Tasklet {

    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;
    private final JdbcTemplate geoTargetJdbc;
    private final JobTableConfig tableConfig;
    private final ChangeDetectionStrategy strategy;

    public ChangeDetectionTasklet(JdbcTemplate sourceJdbc,
                                  JdbcTemplate targetJdbc,
                                  JdbcTemplate geoTargetJdbc,
                                  JobTableConfig tableConfig,
                                  ChangeDetectionStrategy strategy) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
        this.geoTargetJdbc = geoTargetJdbc;
        this.tableConfig = tableConfig;
        this.strategy = strategy;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Executing change detection with strategy={} for table={}",
                strategy.getType(), tableConfig.getSourceTable());
        strategy.detectChanges(sourceJdbc, targetJdbc, geoTargetJdbc, tableConfig, chunkContext);
        return RepeatStatus.FINISHED;
    }
}
