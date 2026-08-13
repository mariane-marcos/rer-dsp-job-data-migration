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
 * Administrative Unit Level 1 (e.g. continent / top-level region).
 */
@Configuration
public class AdministrativeUnitLevel1GeoserverConfig extends AdministrativeUnitGeoserverConfig {

    public static final String JOB_NAME = "adminUnitLevel1GeoserverJob";
    public static final String NAME_PREFIX = "adminUnitLevel1";

    private final JobTableConfig tableConfig;

    public AdministrativeUnitLevel1GeoserverConfig(
            ParallelizationConfig parallelizationConfig,
            ParallelizationMonitorListener parallelizationMonitorListener,
            ChangeDecider changeDecider,
            GeoCacheUpdateListener geoCacheUpdateListener,
            ChangeDetectionStrategyResolver strategyResolver,
            AdministrativeUnitPersistenceService persistenceService,
            SyncStateRepository syncStateRepository,
            SyncWatermarkCommitListener syncWatermarkCommitListener,
            @Qualifier("adminUnitLevel1TableConfig") JobTableConfig tableConfig) {
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
    public Job adminUnitLevel1GeoserverJob(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel1ChangeDetectionStep") Step changeDetectionStep,
            @Qualifier("adminUnitLevel1GeoserverMasterStep") Step masterStep) {
        return buildJob(jobRepository, changeDetectionStep, masterStep);
    }

    @Bean(name = "adminUnitLevel1ChangeDetectionStep")
    public Step adminUnitLevel1ChangeDetectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource,
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource) {
        return buildChangeDetectionStep(
                jobRepository, transactionManager, sourceDataSource, targetDataSource, geoTargetDataSource);
    }

    @Bean(name = "adminUnitLevel1GeoserverMasterStep")
    public Step adminUnitLevel1GeoserverMasterStep(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel1GeoserverWorkerStep") Step workerStep,
            @Qualifier("adminUnitLevel1GeoserverPartitioner") Partitioner partitioner,
            @Qualifier("adminUnitLevel1GeoserverTaskExecutor") TaskExecutor taskExecutor) {
        return buildMasterStep(jobRepository, workerStep, partitioner, taskExecutor);
    }

    @Bean(name = "adminUnitLevel1GeoserverWorkerStep")
    public Step adminUnitLevel1GeoserverWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("adminUnitLevel1GeoserverReader") ItemReader<AdministrativeUnitDTO> reader,
            @Qualifier("adminUnitLevel1GeoserverProcessor")
            ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> processor,
            @Qualifier("adminUnitLevel1GeoserverWriter") ItemWriter<AdministrativeUnitDTO> writer) {
        return buildWorkerStep(jobRepository, transactionManager, reader, processor, writer);
    }

    @Bean(name = "adminUnitLevel1GeoserverPartitioner")
    public Partitioner adminUnitLevel1GeoserverPartitioner(
            @Qualifier("sourceDataSource") DataSource sourceDataSource) {
        return buildPartitioner(sourceDataSource);
    }

    @Bean(name = "adminUnitLevel1GeoserverReader")
    @StepScope
    public AdministrativeUnitGeoserverReader adminUnitLevel1GeoserverReader(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return buildReader(sourceDataSource, minId, maxId);
    }

    @Bean(name = "adminUnitLevel1GeoserverProcessor")
    public ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> adminUnitLevel1GeoserverProcessor() {
        return buildProcessor();
    }

    @Bean(name = "adminUnitLevel1GeoserverWriter")
    public ItemWriter<AdministrativeUnitDTO> adminUnitLevel1GeoserverWriter() {
        return buildWriter();
    }
}
