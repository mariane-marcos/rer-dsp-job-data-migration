package br.car.dsp_batch.batch.config;

import br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategyResolver;
import br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategyType;
import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import br.car.dsp_batch.batch.listener.GeoCacheUpdateListener;
import br.car.dsp_batch.batch.listener.ParallelizationMonitorListener;
import br.car.dsp_batch.batch.partitioner.DeferredWatermarkPartitioner;
import br.car.dsp_batch.batch.reader.AdministrativeUnitGeoserverReader;
import br.car.dsp_batch.service.AdministrativeUnitPersistenceService;
import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.sync.SyncWatermarkCommitListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Instant;

/**
 * Job Spring Batch to update Geoserver and DSP Backend for Area of Interest.
 * Reuses the administrative unit flow and supports {@code WATERMARK} incremental sync.
 */
@Configuration
public class AreaOfInterestGeoserverConfig extends AdministrativeUnitGeoserverConfig {

    public static final String JOB_NAME = "areaOfInterestGeoserverJob";
    public static final String NAME_PREFIX = "areaOfInterest";

    private final JobTableConfig tableConfig;
    private final SyncStateRepository syncStateRepository;
    private final SyncWatermarkCommitListener syncWatermarkCommitListener;

    public AreaOfInterestGeoserverConfig(
            ParallelizationConfig parallelizationConfig,
            ParallelizationMonitorListener parallelizationMonitorListener,
            ChangeDecider changeDecider,
            GeoCacheUpdateListener geoCacheUpdateListener,
            ChangeDetectionStrategyResolver strategyResolver,
            AdministrativeUnitPersistenceService persistenceService,
            SyncStateRepository syncStateRepository,
            SyncWatermarkCommitListener syncWatermarkCommitListener,
            @Qualifier("areaOfInterestTableConfig") JobTableConfig tableConfig) {
        super(parallelizationConfig, parallelizationMonitorListener, changeDecider,
                geoCacheUpdateListener, strategyResolver, persistenceService);
        this.tableConfig = tableConfig;
        this.syncStateRepository = syncStateRepository;
        this.syncWatermarkCommitListener = syncWatermarkCommitListener;
    }

    @Override
    protected String jobName() {
        return JOB_NAME;
    }

    @Override
    protected String namePrefix() {
        return NAME_PREFIX;
    }

    @Override
    protected JobTableConfig tableConfig() {
        return tableConfig;
    }

    @Bean(name = JOB_NAME)
    public Job areaOfInterestGeoserverJob(
            JobRepository jobRepository,
            GeoCacheUpdateListener geoCacheUpdateListener,
            @Qualifier("areaOfInterestChangeDetectionStep") Step changeDetectionStep,
            @Qualifier("areaOfInterestGeoserverMasterStep") Step masterStep) {
        return new JobBuilder(jobName(), jobRepository)
                .start(changeDetectionStep)
                .next(changeDecider)
                .on("PROCESS").to(masterStep)
                .from(changeDecider).on("SKIP").end()
                .from(changeDecider).on("*").end()
                .end()
                .listener(geoCacheUpdateListener)
                .listener(syncWatermarkCommitListener)
                .build();
    }

    @Bean(name = "areaOfInterestChangeDetectionStep")
    public Step areaOfInterestChangeDetectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource,
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource) {
        return buildChangeDetectionStep(
                jobRepository, transactionManager, sourceDataSource, targetDataSource, geoTargetDataSource);
    }

    @Bean(name = "areaOfInterestGeoserverMasterStep")
    public Step areaOfInterestGeoserverMasterStep(
            JobRepository jobRepository,
            @Qualifier("areaOfInterestGeoserverWorkerStep") Step workerStep,
            @Qualifier("areaOfInterestGeoserverPartitioner") Partitioner partitioner,
            @Qualifier("areaOfInterestGeoserverTaskExecutor") TaskExecutor taskExecutor) {
        return buildMasterStep(jobRepository, workerStep, partitioner, taskExecutor);
    }

    @Bean(name = "areaOfInterestGeoserverWorkerStep")
    public Step areaOfInterestGeoserverWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("areaOfInterestGeoserverReader") ItemReader<AdministrativeUnitDTO> reader,
            @Qualifier("areaOfInterestGeoserverProcessor")
            ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> processor,
            @Qualifier("areaOfInterestGeoserverWriter") ItemWriter<AdministrativeUnitDTO> writer) {
        return buildWorkerStep(jobRepository, transactionManager, reader, processor, writer);
    }

    @Bean(name = "areaOfInterestGeoserverPartitioner")
    public Partitioner areaOfInterestGeoserverPartitioner(
            @Qualifier("sourceDataSource") DataSource sourceDataSource) {
        return new DeferredWatermarkPartitioner(sourceDataSource, tableConfig, syncStateRepository);
    }

    @Bean(name = "areaOfInterestGeoserverReader")
    @StepScope
    public AdministrativeUnitGeoserverReader areaOfInterestGeoserverReader(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return buildWatermarkAwareReader(sourceDataSource, minId, maxId);
    }

    @Bean(name = "areaOfInterestGeoserverProcessor")
    public ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> areaOfInterestGeoserverProcessor() {
        return buildProcessor();
    }

    @Bean(name = "areaOfInterestGeoserverWriter")
    public ItemWriter<AdministrativeUnitDTO> areaOfInterestGeoserverWriter() {
        return buildWriter();
    }

    private AdministrativeUnitGeoserverReader buildWatermarkAwareReader(DataSource sourceDataSource,
                                                                        Long minId,
                                                                        Long maxId) {
        Instant watermark = null;
        String updatedAtColumn = null;
        if (usesWatermarkStrategy()) {
            updatedAtColumn = tableConfig.getUpdatedAtColumn();
            watermark = syncStateRepository.findWatermark(tableConfig.getSyncKey()).orElse(null);
        }
        return new AdministrativeUnitGeoserverReader(
                sourceDataSource,
                minId,
                maxId,
                parallelizationConfig.getJobSettings(jobName()).getPageSize(),
                tableConfig,
                watermark,
                updatedAtColumn,
                namePrefix() + "GeoserverReader"
        );
    }

    private boolean usesWatermarkStrategy() {
        return tableConfig.getChangeDetectionStrategy() == ChangeDetectionStrategyType.WATERMARK;
    }
}
