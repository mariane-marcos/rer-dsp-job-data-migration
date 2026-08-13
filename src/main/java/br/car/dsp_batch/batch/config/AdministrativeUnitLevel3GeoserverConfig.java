package br.car.dsp_batch.batch.config;

import br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategyResolver;
import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import br.car.dsp_batch.batch.listener.GeoCacheUpdateListener;
import br.car.dsp_batch.batch.listener.ParallelizationMonitorListener;
import br.car.dsp_batch.batch.reader.AdministrativeUnitGeoserverReader;
import br.car.dsp_batch.service.AdministrativeUnitPersistenceService;
import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.sync.SyncWatermarkCommitListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
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

/**
 * Administrative Unit Level 3 (contained within Level 2, e.g. admin division).
 */
@Configuration
public class AdministrativeUnitLevel3GeoserverConfig extends AdministrativeUnitGeoserverConfig {

    public static final String JOB_NAME = "adminUnitLevel3GeoserverJob";
    public static final String NAME_PREFIX = "adminUnitLevel3";

    private final JobTableConfig tableConfig;

    public AdministrativeUnitLevel3GeoserverConfig(
            ParallelizationConfig parallelizationConfig,
            ParallelizationMonitorListener parallelizationMonitorListener,
            ChangeDecider changeDecider,
            GeoCacheUpdateListener geoCacheUpdateListener,
            ChangeDetectionStrategyResolver strategyResolver,
            AdministrativeUnitPersistenceService persistenceService,
            SyncStateRepository syncStateRepository,
            SyncWatermarkCommitListener syncWatermarkCommitListener,
            @Qualifier("adminUnitLevel3TableConfig") JobTableConfig tableConfig) {
        super(parallelizationConfig, parallelizationMonitorListener, changeDecider,
                geoCacheUpdateListener, strategyResolver, persistenceService,
                syncStateRepository, syncWatermarkCommitListener);
        this.tableConfig = tableConfig;
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
    public Job adminUnitLevel3GeoserverJob(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel3ChangeDetectionStep") Step changeDetectionStep,
            @Qualifier("adminUnitLevel3GeoserverMasterStep") Step masterStep) {
        return buildJob(jobRepository, changeDetectionStep, masterStep);
    }

    @Bean(name = "adminUnitLevel3ChangeDetectionStep")
    public Step adminUnitLevel3ChangeDetectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource,
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource) {
        return buildChangeDetectionStep(
                jobRepository, transactionManager, sourceDataSource, targetDataSource, geoTargetDataSource);
    }

    @Bean(name = "adminUnitLevel3GeoserverMasterStep")
    public Step adminUnitLevel3GeoserverMasterStep(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel3GeoserverWorkerStep") Step workerStep,
            @Qualifier("adminUnitLevel3GeoserverPartitioner") Partitioner partitioner,
            @Qualifier("adminUnitLevel3GeoserverTaskExecutor") TaskExecutor taskExecutor) {
        return buildMasterStep(jobRepository, workerStep, partitioner, taskExecutor);
    }

    @Bean(name = "adminUnitLevel3GeoserverWorkerStep")
    public Step adminUnitLevel3GeoserverWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("adminUnitLevel3GeoserverReader") ItemReader<AdministrativeUnitDTO> reader,
            @Qualifier("adminUnitLevel3GeoserverProcessor")
            ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> processor,
            @Qualifier("adminUnitLevel3GeoserverWriter") ItemWriter<AdministrativeUnitDTO> writer) {
        return buildWorkerStep(jobRepository, transactionManager, reader, processor, writer);
    }

    @Bean(name = "adminUnitLevel3GeoserverPartitioner")
    public Partitioner adminUnitLevel3GeoserverPartitioner(
            @Qualifier("sourceDataSource") DataSource sourceDataSource) {
        return buildPartitioner(sourceDataSource);
    }

    @Bean(name = "adminUnitLevel3GeoserverReader")
    @StepScope
    public AdministrativeUnitGeoserverReader adminUnitLevel3GeoserverReader(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return buildReader(sourceDataSource, minId, maxId);
    }

    @Bean(name = "adminUnitLevel3GeoserverProcessor")
    public ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> adminUnitLevel3GeoserverProcessor() {
        return buildProcessor();
    }

    @Bean(name = "adminUnitLevel3GeoserverWriter")
    public ItemWriter<AdministrativeUnitDTO> adminUnitLevel3GeoserverWriter() {
        return buildWriter();
    }
}
