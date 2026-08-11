package br.car.dsp_batch.batch.config.strategy;

import br.car.dsp_batch.batch.config.table.AdministrativeUnitTableProperties;
import br.car.dsp_batch.batch.config.table.AreaOfInterestTableProperties;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChangeDetectionStrategyResolverTest {

    @Test
    void resolvesDefaultStrategy() {
        DefaultChangeDetectionStrategy defaultStrategy = new DefaultChangeDetectionStrategy();
        DateRangeChangeDetectionStrategy dateRangeStrategy = new DateRangeChangeDetectionStrategy();
        ChangeDetectionStrategyResolver resolver =
                new ChangeDetectionStrategyResolver(List.of(defaultStrategy, dateRangeStrategy));

        ChangeDetectionStrategy resolved = resolver.resolve(ChangeDetectionStrategyType.DEFAULT);

        assertEquals(ChangeDetectionStrategyType.DEFAULT, resolved.getType());
        assertNotNull(resolved);
    }

    @Test
    void resolvesDateRangeStrategy() {
        DefaultChangeDetectionStrategy defaultStrategy = new DefaultChangeDetectionStrategy();
        DateRangeChangeDetectionStrategy dateRangeStrategy = new DateRangeChangeDetectionStrategy();
        WatermarkChangeDetectionStrategy watermarkStrategy =
                new WatermarkChangeDetectionStrategy(null);
        ChangeDetectionStrategyResolver resolver =
                new ChangeDetectionStrategyResolver(
                        List.of(defaultStrategy, dateRangeStrategy, watermarkStrategy));

        ChangeDetectionStrategy resolved = resolver.resolve(ChangeDetectionStrategyType.DATE_RANGE);

        assertEquals(ChangeDetectionStrategyType.DATE_RANGE, resolved.getType());
        assertNotNull(resolved);
    }

    @Test
    void resolvesWatermarkStrategy() {
        DefaultChangeDetectionStrategy defaultStrategy = new DefaultChangeDetectionStrategy();
        DateRangeChangeDetectionStrategy dateRangeStrategy = new DateRangeChangeDetectionStrategy();
        WatermarkChangeDetectionStrategy watermarkStrategy =
                new WatermarkChangeDetectionStrategy(null);
        ChangeDetectionStrategyResolver resolver =
                new ChangeDetectionStrategyResolver(
                        List.of(defaultStrategy, dateRangeStrategy, watermarkStrategy));

        ChangeDetectionStrategy resolved = resolver.resolve(ChangeDetectionStrategyType.WATERMARK);

        assertEquals(ChangeDetectionStrategyType.WATERMARK, resolved.getType());
        assertNotNull(resolved);
    }

    @Test
    void defaultStrategyWritesNoChangesWhenBothSidesEmpty() {
        DefaultChangeDetectionStrategy strategy = new DefaultChangeDetectionStrategy();
        JdbcTemplate sourceJdbc = mock(JdbcTemplate.class);
        JdbcTemplate targetJdbc = mock(JdbcTemplate.class);
        ChunkContext chunkContext = mock(ChunkContext.class);
        StepContext stepContext = mock(StepContext.class);
        StepExecution stepExecution = mock(StepExecution.class);
        JobExecution jobExecution = mock(JobExecution.class);
        ExecutionContext executionContext = new ExecutionContext();

        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getStepExecution()).thenReturn(stepExecution);
        when(stepExecution.getJobExecution()).thenReturn(jobExecution);
        when(jobExecution.getExecutionContext()).thenReturn(executionContext);

        when(targetJdbc.query(anyString(), any(ResultSetExtractor.class)))
                .thenReturn(Collections.emptyMap());
        doAnswer(invocation -> null)
                .when(sourceJdbc).query(anyString(), any(RowCallbackHandler.class));

        AdministrativeUnitTableProperties tableConfig = new AdministrativeUnitTableProperties();
        tableConfig.setSourceTable("source.unit");
        tableConfig.setTargetTable("target.unit");
        tableConfig.setPrimaryKey("id");
        tableConfig.setGeometryColumn("geom");
        tableConfig.setWhereClause("1=1");
        tableConfig.setComparisonColumns(List.of("name"));
        tableConfig.setPersistColumns(List.of("id", "name"));
        tableConfig.setLayerName("unit");
        tableConfig.setSrid(4326);

        JdbcTemplate geoTargetJdbc = mock(JdbcTemplate.class);
        when(geoTargetJdbc.query(anyString(), any(ResultSetExtractor.class)))
                .thenReturn(Collections.emptyMap());

        strategy.detectChanges(sourceJdbc, targetJdbc, geoTargetJdbc, tableConfig, chunkContext);

        assertEquals(false, executionContext.get(DefaultChangeDetectionStrategy.CTX_HAS_CHANGES));
    }

    @Test
    void dateRangeStrategyWritesNoChangesWhenSourceEmpty() {
        DateRangeChangeDetectionStrategy strategy = new DateRangeChangeDetectionStrategy();
        JdbcTemplate sourceJdbc = mock(JdbcTemplate.class);
        JdbcTemplate targetJdbc = mock(JdbcTemplate.class);
        JdbcTemplate geoTargetJdbc = mock(JdbcTemplate.class);
        ChunkContext chunkContext = mock(ChunkContext.class);
        StepContext stepContext = mock(StepContext.class);
        StepExecution stepExecution = mock(StepExecution.class);
        JobExecution jobExecution = mock(JobExecution.class);
        ExecutionContext executionContext = new ExecutionContext();

        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getStepExecution()).thenReturn(stepExecution);
        when(stepExecution.getJobExecution()).thenReturn(jobExecution);
        when(jobExecution.getExecutionContext()).thenReturn(executionContext);

        doAnswer(invocation -> null)
                .when(sourceJdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        AreaOfInterestTableProperties tableConfig = new AreaOfInterestTableProperties();
        tableConfig.setSourceTable("property");
        tableConfig.setTargetTable("property");
        tableConfig.setPrimaryKey("id");
        tableConfig.setGeometryColumn("geometry");
        tableConfig.setWhereClause("1=1");
        tableConfig.setComparisonColumns(List.of("created_date"));
        tableConfig.setPersistColumns(List.of("id", "property_name"));
        tableConfig.setLayerName("property");
        tableConfig.setSrid(4674);
        tableConfig.setStartDate(java.time.LocalDate.of(2024, 1, 1));
        tableConfig.setEndDate(java.time.LocalDate.of(2024, 12, 31));

        strategy.detectChanges(sourceJdbc, targetJdbc, geoTargetJdbc, tableConfig, chunkContext);

        assertEquals(false, executionContext.get(DefaultChangeDetectionStrategy.CTX_HAS_CHANGES));
    }
}
