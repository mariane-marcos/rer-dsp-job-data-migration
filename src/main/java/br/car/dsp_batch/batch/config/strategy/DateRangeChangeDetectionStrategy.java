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
 * Date-range change detection strategy.
 * Treats records as changed when comparison column(s) (e.g. {@code created_date})
 * fall between {@code startDate} and {@code endDate}, inclusive.
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
                              JdbcTemplate geoTargetJdbc,
                              JobTableConfig tableConfig,
                              ChunkContext chunkContext) {
        LocalDate startDate = tableConfig.getStartDate();
        LocalDate endDate = tableConfig.getEndDate();

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                    "DATE_RANGE strategy requires startDate and endDate in JobTableConfig for table "
                            + tableConfig.getSourceTable());
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "startDate (" + startDate + ") must not be after endDate (" + endDate + ")");
        }

        List<String> comparisonColumns = tableConfig.getComparisonColumns();
        if (comparisonColumns == null || comparisonColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "DATE_RANGE strategy requires comparison-columns for table "
                            + tableConfig.getSourceTable());
        }

        log.info("Starting date-range change detection for table={} ({} to {}) on columns={}",
                tableConfig.getSourceTable(), startDate, endDate, comparisonColumns);

        List<String> bboxes = fetchAffectedBboxes(sourceJdbc, tableConfig, startDate, endDate);

        var jobContext = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        if (bboxes.isEmpty()) {
            log.info("No changes in date range for {}", tableConfig.getSourceTable());
            jobContext.put(DefaultChangeDetectionStrategy.CTX_HAS_CHANGES, false);
        } else {
            log.info("Detected {} areas with changes in date range for {}",
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

        log.info("Source: {} records in date range", bboxes.size());
        return bboxes;
    }

    /**
     * Builds the query using only {@link JobTableConfig} properties
     * (table, geometry, SRID, where-clause and comparison columns).
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
