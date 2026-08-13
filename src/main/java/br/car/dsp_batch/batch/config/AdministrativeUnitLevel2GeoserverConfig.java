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
 * Administrative Unit Level 2 (contained within Level 1, e.g. country).
 */
@Configuration
public class AdministrativeUnitLevel2GeoserverConfig extends AdministrativeUnitGeoserverConfig {

    public static final String JOB_NAME = "adminUnitLevel2GeoserverJob";
    public static final String NAME_PREFIX = "adminUnitLevel2";

    private final JobTableConfig tableConfig;

    public AdministrativeUnitLevel2GeoserverConfig(
            ParallelizationConfig parallelizationConfig,
            ParallelizationMonitorListener parallelizationMonitorListener,
            ChangeDecider changeDecider,
            GeoCacheUpdateListener geoCacheUpdateListener,
            ChangeDetectionStrategyResolver strategyResolver,
            AdministrativeUnitPersistenceService persistenceService,
            SyncStateRepository syncStateRepository,
            SyncWatermarkCommitListener syncWatermarkCommitListener,
            @Qualifier("adminUnitLevel2TableConfig") JobTableConfig tableConfig) {
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
    public Job adminUnitLevel2GeoserverJob(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel2ChangeDetectionStep") Step changeDetectionStep,
            @Qualifier("adminUnitLevel2GeoserverMasterStep") Step masterStep) {
        return buildJob(jobRepository, changeDetectionStep, masterStep);
    }

    @Bean(name = "adminUnitLevel2ChangeDetectionStep")
    public Step adminUnitLevel2ChangeDetectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource,
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource) {
        return buildChangeDetectionStep(
                jobRepository, transactionManager, sourceDataSource, targetDataSource, geoTargetDataSource);
    }

    @Bean(name = "adminUnitLevel2GeoserverMasterStep")
    public Step adminUnitLevel2GeoserverMasterStep(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel2GeoserverWorkerStep") Step workerStep,
            @Qualifier("adminUnitLevel2GeoserverPartitioner") Partitioner partitioner,
            @Qualifier("adminUnitLevel2GeoserverTaskExecutor") TaskExecutor taskExecutor) {
        return buildMasterStep(jobRepository, workerStep, partitioner, taskExecutor);
    }

    @Bean(name = "adminUnitLevel2GeoserverWorkerStep")
    public Step adminUnitLevel2GeoserverWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("adminUnitLevel2GeoserverReader") ItemReader<AdministrativeUnitDTO> reader,
            @Qualifier("adminUnitLevel2GeoserverProcessor")
            ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> processor,
            @Qualifier("adminUnitLevel2GeoserverWriter") ItemWriter<AdministrativeUnitDTO> writer) {
        return buildWorkerStep(jobRepository, transactionManager, reader, processor, writer);
    }

    @Bean(name = "adminUnitLevel2GeoserverPartitioner")
    public Partitioner adminUnitLevel2GeoserverPartitioner(
            @Qualifier("sourceDataSource") DataSource sourceDataSource) {
        return buildPartitioner(sourceDataSource);
    }

    @Bean(name = "adminUnitLevel2GeoserverReader")
    @StepScope
    public AdministrativeUnitGeoserverReader adminUnitLevel2GeoserverReader(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return buildReader(sourceDataSource, minId, maxId);
    }

    @Bean(name = "adminUnitLevel2GeoserverProcessor")
    public ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> adminUnitLevel2GeoserverProcessor() {
        return buildProcessor();
    }

    @Bean(name = "adminUnitLevel2GeoserverWriter")
    public ItemWriter<AdministrativeUnitDTO> adminUnitLevel2GeoserverWriter() {
        return buildWriter();
    }
}
