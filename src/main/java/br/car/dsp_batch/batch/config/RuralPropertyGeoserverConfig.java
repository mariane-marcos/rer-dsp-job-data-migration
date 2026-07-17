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
 * Job Spring Batch de atualização Geoserver para Rural Properties.
 * Reutiliza o fluxo de unidades administrativas, com estratégia {@code DATE_RANGE}.
 */
@Configuration
public class RuralPropertyGeoserverConfig extends AdministrativeUnitGeoserverConfig {

    public static final String JOB_NAME = "ruralPropertyGeoserverJob";
    public static final String NAME_PREFIX = "ruralProperty";

    private final JobTableConfig tableConfig;

    public RuralPropertyGeoserverConfig(
            ParallelizationConfig parallelizationConfig,
            ParallelizationMonitorListener parallelizationMonitorListener,
            ChangeDecider changeDecider,
            GeoCacheUpdateListener geoCacheUpdateListener,
            ChangeDetectionStrategyResolver strategyResolver,
            AdministrativeUnitPersistenceService persistenceService,
            @Qualifier("ruralPropertyTableConfig") JobTableConfig tableConfig) {
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
    public Job ruralPropertyGeoserverJob(
            JobRepository jobRepository,
            @Qualifier("ruralPropertyChangeDetectionStep") Step changeDetectionStep,
            @Qualifier("ruralPropertyGeoserverMasterStep") Step masterStep) {
        return buildJob(jobRepository, changeDetectionStep, masterStep);
    }

    @Bean(name = "ruralPropertyChangeDetectionStep")
    public Step ruralPropertyChangeDetectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource) {
        return buildChangeDetectionStep(jobRepository, transactionManager, sourceDataSource, targetDataSource);
    }

    @Bean(name = "ruralPropertyGeoserverMasterStep")
    public Step ruralPropertyGeoserverMasterStep(
            JobRepository jobRepository,
            @Qualifier("ruralPropertyGeoserverWorkerStep") Step workerStep,
            @Qualifier("ruralPropertyGeoserverPartitioner") Partitioner partitioner,
            @Qualifier("ruralPropertyGeoserverTaskExecutor") TaskExecutor taskExecutor) {
        return buildMasterStep(jobRepository, workerStep, partitioner, taskExecutor);
    }

    @Bean(name = "ruralPropertyGeoserverWorkerStep")
    public Step ruralPropertyGeoserverWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("ruralPropertyGeoserverReader") ItemReader<AdministrativeUnitDTO> reader,
            @Qualifier("ruralPropertyGeoserverProcessor")
            ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> processor,
            @Qualifier("ruralPropertyGeoserverWriter") ItemWriter<AdministrativeUnitDTO> writer) {
        return buildWorkerStep(jobRepository, transactionManager, reader, processor, writer);
    }

    @Bean(name = "ruralPropertyGeoserverPartitioner")
    public Partitioner ruralPropertyGeoserverPartitioner(
            @Qualifier("sourceDataSource") DataSource sourceDataSource) {
        return buildPartitioner(sourceDataSource);
    }

    @Bean(name = "ruralPropertyGeoserverReader")
    @StepScope
    public AdministrativeUnitGeoserverReader ruralPropertyGeoserverReader(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return buildReader(sourceDataSource, minId, maxId);
    }

    @Bean(name = "ruralPropertyGeoserverProcessor")
    public ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> ruralPropertyGeoserverProcessor() {
        return buildProcessor();
    }

    @Bean(name = "ruralPropertyGeoserverWriter")
    public ItemWriter<AdministrativeUnitDTO> ruralPropertyGeoserverWriter() {
        return buildWriter();
    }
}
