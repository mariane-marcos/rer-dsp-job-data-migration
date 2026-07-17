package br.car.dsp_batch.batch.config;

import br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategy;
import br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategyResolver;
import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import br.car.dsp_batch.batch.listener.GeoCacheUpdateListener;
import br.car.dsp_batch.batch.listener.ParallelizationMonitorListener;
import br.car.dsp_batch.batch.partitioner.ColumnRangePartitioner;
import br.car.dsp_batch.batch.processor.AdministrativeUnitGeoserverProcessor;
import br.car.dsp_batch.batch.reader.AdministrativeUnitGeoserverReader;
import br.car.dsp_batch.batch.tasklet.ChangeDetectionTasklet;
import br.car.dsp_batch.batch.writer.AdministrativeUnitGeoserverWriter;
import br.car.dsp_batch.service.AdministrativeUnitPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Abstract Spring Batch configuration for administrative unit Geoserver updates.
 * Subclasses supply only job-specific identity and {@link JobTableConfig}.
 */
public abstract class AdministrativeUnitGeoserverConfig {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ParallelizationConfig parallelizationConfig;
    private final ParallelizationMonitorListener parallelizationMonitorListener;
    private final ChangeDecider changeDecider;
    private final GeoCacheUpdateListener geoCacheUpdateListener;
    private final ChangeDetectionStrategyResolver strategyResolver;
    private final AdministrativeUnitPersistenceService persistenceService;

    protected AdministrativeUnitGeoserverConfig(
            ParallelizationConfig parallelizationConfig,
            ParallelizationMonitorListener parallelizationMonitorListener,
            ChangeDecider changeDecider,
            GeoCacheUpdateListener geoCacheUpdateListener,
            ChangeDetectionStrategyResolver strategyResolver,
            AdministrativeUnitPersistenceService persistenceService) {
        this.parallelizationConfig = parallelizationConfig;
        this.parallelizationMonitorListener = parallelizationMonitorListener;
        this.changeDecider = changeDecider;
        this.geoCacheUpdateListener = geoCacheUpdateListener;
        this.strategyResolver = strategyResolver;
        this.persistenceService = persistenceService;
    }

    /** Unique Spring Batch job name. */
    protected abstract String jobName();

    /** Prefix used to build step and component names (e.g. {@code adminUnitLevel3}). */
    protected abstract String namePrefix();

    /** Table-specific configuration for this administrative unit level. */
    protected abstract JobTableConfig tableConfig();

    protected Job buildJob(JobRepository jobRepository,
                           Step changeDetectionStep,
                           Step masterStep) {
        return new JobBuilder(jobName(), jobRepository)
                .start(changeDetectionStep)
                .next(changeDecider)
                .on("PROCESS").to(masterStep)
                .from(changeDecider).on("SKIP").end()
                .from(changeDecider).on("*").end()
                .end()
                .listener(geoCacheUpdateListener)
                .build();
    }

    protected Step buildChangeDetectionStep(JobRepository jobRepository,
                                            PlatformTransactionManager transactionManager,
                                            DataSource sourceDataSource,
                                            DataSource targetDataSource) {
        JobTableConfig tableConfig = tableConfig();
        ChangeDetectionStrategy strategy =
                strategyResolver.resolve(tableConfig.getChangeDetectionStrategy());

        JdbcTemplate sourceJdbc = new JdbcTemplate(sourceDataSource);
        JdbcTemplate targetJdbc = new JdbcTemplate(targetDataSource);

        String stepName = namePrefix() + "ChangeDetectionStep";
        return new StepBuilder(stepName, jobRepository)
                .tasklet(new ChangeDetectionTasklet(sourceJdbc, targetJdbc, tableConfig, strategy),
                        transactionManager)
                .build();
    }

    protected Step buildMasterStep(JobRepository jobRepository,
                                   Step workerStep,
                                   Partitioner partitioner,
                                   TaskExecutor taskExecutor) {
        ParallelizationConfig.ParallelizationSettings settings =
                parallelizationConfig.getJobSettings(jobName());

        if (!settings.isEnabled()) {
            logger.warn("{} - Partitioning disabled.", jobName());
            return workerStep;
        }

        logger.info("{} - Configuring master step with {} partitions.",
                jobName(), settings.getThreadPoolSize());

        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setStep(workerStep);
        partitionHandler.setTaskExecutor(taskExecutor);
        partitionHandler.setGridSize(settings.getThreadPoolSize());

        String stepName = namePrefix() + "GeoserverMasterStep";
        return new StepBuilder(stepName, jobRepository)
                .partitioner(workerStep.getName(), partitioner)
                .partitionHandler(partitionHandler)
                .build();
    }

    protected Step buildWorkerStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   ItemReader<AdministrativeUnitDTO> reader,
                                   ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> processor,
                                   ItemWriter<AdministrativeUnitDTO> writer) {
        ParallelizationConfig.ParallelizationSettings settings =
                parallelizationConfig.getJobSettings(jobName());

        String stepName = namePrefix() + "GeoserverWorkerStep";
        return new StepBuilder(stepName, jobRepository)
                .<AdministrativeUnitDTO, AdministrativeUnitDTO>chunk(settings.getChunkSize(), transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(parallelizationMonitorListener)
                .build();
    }

    protected Partitioner buildPartitioner(DataSource sourceDataSource) {
        JobTableConfig tableConfig = tableConfig();
        String where = "1=1".equals(tableConfig.getWhereClause()) ? null : tableConfig.getWhereClause();
        return new ColumnRangePartitioner(
                sourceDataSource,
                tableConfig.getSourceTable(),
                tableConfig.getPartitionColumn(),
                where
        );
    }

    protected AdministrativeUnitGeoserverReader buildReader(DataSource sourceDataSource,
                                                            Long minId,
                                                            Long maxId) {
        ParallelizationConfig.ParallelizationSettings settings =
                parallelizationConfig.getJobSettings(jobName());
        return new AdministrativeUnitGeoserverReader(
                sourceDataSource,
                minId,
                maxId,
                settings.getPageSize(),
                tableConfig(),
                namePrefix() + "GeoserverReader"
        );
    }

    protected ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> buildProcessor() {
        return new AdministrativeUnitGeoserverProcessor();
    }

    protected ItemWriter<AdministrativeUnitDTO> buildWriter() {
        return new AdministrativeUnitGeoserverWriter(persistenceService, tableConfig());
    }
}
