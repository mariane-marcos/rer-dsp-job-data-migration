package br.car.dsp_batch.batch.config;

import br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategyResolver;
import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import br.car.dsp_batch.batch.listener.GeoCacheUpdateListener;
import br.car.dsp_batch.batch.listener.ParallelizationMonitorListener;
import br.car.dsp_batch.batch.reader.AdministrativeUnitGeoserverReader;
import br.car.dsp_batch.service.AdministrativeUnitPersistenceService;
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
 * Job Spring Batch to update Geoserver and DSP Backend for Area of Interest.
 * Reuses the flow of administrative units.
 */
@Configuration
public class AreaOfInterestGeoserverConfig extends AdministrativeUnitGeoserverConfig {

    public static final String JOB_NAME = "areaOfInterestGeoserverJob";
    public static final String NAME_PREFIX = "areaOfInterest";

    private final JobTableConfig tableConfig;

    public AreaOfInterestGeoserverConfig(
            ParallelizationConfig parallelizationConfig,
            ParallelizationMonitorListener parallelizationMonitorListener,
            ChangeDecider changeDecider,
            GeoCacheUpdateListener geoCacheUpdateListener,
            ChangeDetectionStrategyResolver strategyResolver,
            AdministrativeUnitPersistenceService persistenceService,
            @Qualifier("areaOfInterestTableConfig") JobTableConfig tableConfig) {
        super(parallelizationConfig, parallelizationMonitorListener, changeDecider,
                geoCacheUpdateListener, strategyResolver, persistenceService);
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
    public Job areaOfInterestGeoserverJob(
            JobRepository jobRepository,
            @Qualifier("areaOfInterestChangeDetectionStep") Step changeDetectionStep,
            @Qualifier("areaOfInterestGeoserverMasterStep") Step masterStep) {
        return buildJob(jobRepository, changeDetectionStep, masterStep);
    }

    @Bean(name = "areaOfInterestChangeDetectionStep")
    public Step areaOfInterestChangeDetectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource) {
        return buildChangeDetectionStep(jobRepository, transactionManager, sourceDataSource, targetDataSource);
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
        return buildPartitioner(sourceDataSource);
    }

    @Bean(name = "areaOfInterestGeoserverReader")
    @StepScope
    public AdministrativeUnitGeoserverReader areaOfInterestGeoserverReader(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return buildReader(sourceDataSource, minId, maxId);
    }

    @Bean(name = "areaOfInterestGeoserverProcessor")
    public ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> areaOfInterestGeoserverProcessor() {
        return buildProcessor();
    }

    @Bean(name = "areaOfInterestGeoserverWriter")
    public ItemWriter<AdministrativeUnitDTO> areaOfInterestGeoserverWriter() {
        return buildWriter();
    }
}
