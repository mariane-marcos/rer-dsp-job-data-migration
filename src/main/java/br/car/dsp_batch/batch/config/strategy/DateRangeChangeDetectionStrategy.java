package br.car.dsp_batch.batch.config.strategy;

import br.car.dsp_batch.batch.config.JobTableConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Estratégia de detecção por intervalo de datas.
 * Considera alterados os registros cuja(s) coluna(s) de comparação
 * (ex.: {@code created_date}) estejam entre {@code startDate} e {@code endDate}, inclusive.
 */
@Slf4j
@Component
public class DateRangeChangeDetectionStrategy implements ChangeDetectionStrategy {

    @Override
    public ChangeDetectionStrategyType getType() {
        return ChangeDetectionStrategyType.DATE_RANGE;
    }

    @Override
    public void detectChanges(JdbcTemplate sourceJdbc,
                              JdbcTemplate targetJdbc,
                              JobTableConfig tableConfig,
                              ChunkContext chunkContext) {
        LocalDate startDate = tableConfig.getStartDate();
        LocalDate endDate = tableConfig.getEndDate();

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                    "Estratégia DATE_RANGE exige startDate e endDate em JobTableConfig para a tabela "
                            + tableConfig.getSourceTable());
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "startDate (" + startDate + ") não pode ser posterior a endDate (" + endDate + ")");
        }

        List<String> comparisonColumns = tableConfig.getComparisonColumns();
        if (comparisonColumns == null || comparisonColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Estratégia DATE_RANGE exige comparison-columns para a tabela "
                            + tableConfig.getSourceTable());
        }

        log.info("Iniciando detecção por intervalo de datas para tabela={} ({} a {}) nas colunas={}",
                tableConfig.getSourceTable(), startDate, endDate, comparisonColumns);

        List<String> bboxes = fetchAffectedBboxes(sourceJdbc, tableConfig, startDate, endDate);

        var jobContext = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        if (bboxes.isEmpty()) {
            log.info("Nenhuma alteração no intervalo para {}", tableConfig.getSourceTable());
            jobContext.put(DefaultChangeDetectionStrategy.CTX_HAS_CHANGES, false);
        } else {
            log.info("Detectadas {} áreas com alterações no intervalo em {}",
                    bboxes.size(), tableConfig.getSourceTable());
            jobContext.put(DefaultChangeDetectionStrategy.CTX_HAS_CHANGES, true);
            jobContext.put(DefaultChangeDetectionStrategy.CTX_AFFECTED_BBOXES, bboxes);
            jobContext.put(DefaultChangeDetectionStrategy.CTX_LAYER_NAME, tableConfig.getLayerName());
        }
    }

    private List<String> fetchAffectedBboxes(JdbcTemplate sourceJdbc,
                                             JobTableConfig tableConfig,
                                             LocalDate startDate,
                                             LocalDate endDate) {
        String sql = buildDateRangeSql(tableConfig);
        Object[] params = buildDateRangeParams(tableConfig, startDate, endDate);

        List<String> bboxes = new ArrayList<>();
        sourceJdbc.query(sql, rs -> {
            bboxes.add(formatBbox(
                    rs.getDouble("minx"),
                    rs.getDouble("miny"),
                    rs.getDouble("maxx"),
                    rs.getDouble("maxy")));
        }, params);

        log.info("Fonte: {} registros no intervalo de datas", bboxes.size());
        return bboxes;
    }

    /**
     * Monta a consulta usando apenas propriedades de {@link JobTableConfig}
     * (tabela, geometria, SRID, where e colunas de comparação).
     */
    private String buildDateRangeSql(JobTableConfig tableConfig) {
        String pk = tableConfig.getPrimaryKey();
        String geom = tableConfig.getGeometryColumn();
        String table = tableConfig.getSourceTable();
        int srid = tableConfig.getSrid();

        String dateFilters = tableConfig.getComparisonColumns().stream()
                .map(col -> "CAST(" + col + " AS DATE) BETWEEN ? AND ?")
                .collect(Collectors.joining(" AND "));

        String whereClause = tableConfig.getWhereClause();
        String baseWhere = (whereClause == null || whereClause.isBlank() || "1=1".equals(whereClause.trim()))
                ? dateFilters
                : whereClause + " AND " + dateFilters;

        String validGeomFilter = String.format(
                "(%s IS NOT NULL"
                        + " AND NOT ST_IsEmpty(ST_Multi(ST_CollectionExtract(ST_MakeValid(COALESCE(%s, ST_Buffer(%s, 0))), 3))))",
                geom, geom, geom
        );

        String finalWhere = "WHERE " + baseWhere + " AND " + validGeomFilter;

        return String.format(
                "SELECT %s, "
                        + "ST_XMin(env3857) as minx, "
                        + "ST_YMin(env3857) as miny, "
                        + "ST_XMax(env3857) as maxx, "
                        + "ST_YMax(env3857) as maxy "
                        + "FROM ("
                        + "  SELECT %s, "
                        + "  ST_Transform(ST_Envelope(ST_SetSRID(%s, %d)), 3857) as env3857 "
                        + "  FROM %s %s"
                        + ") t",
                pk, pk, geom, srid, table, finalWhere
        );
    }

    private Object[] buildDateRangeParams(JobTableConfig tableConfig,
                                          LocalDate startDate,
                                          LocalDate endDate) {
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < tableConfig.getComparisonColumns().size(); i++) {
            params.add(startDate);
            params.add(endDate);
        }
        return params.toArray();
    }

    private String formatBbox(double minX, double minY, double maxX, double maxY) {
        return String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", minX, minY, maxX, maxY);
    }
}
