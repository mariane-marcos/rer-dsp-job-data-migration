package br.car.dsp_batch.batch.partitioner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Partitions a numeric column into contiguous ranges for parallel workers.
 * If the column is alphanumeric, creates a single partition without a min/max range.
 */
@Slf4j
public class ColumnRangePartitioner implements Partitioner {

    private final JdbcOperations jdbcTemplate;
    private final String table;
    private final String column;
    private final String whereClause;

    public ColumnRangePartitioner(DataSource dataSource, String table, String column, String whereClause) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.table = table;
        this.column = column;
        this.whereClause = whereClause;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        if (!isNumericColumn()) {
            log.info(
                    "Coluna '{}' em '{}' não é numérica (ou está vazia). "
                            + "Criando uma única partição sem faixa minId/maxId.",
                    column, table);
            return singlePartitionWithoutRange();
        }

        // CAST garante MIN/MAX numérico mesmo se a coluna for varchar com dígitos (ex.: cd_uf).
        String numericColumn = "CAST(" + column + " AS BIGINT)";
        String minMaxSql = String.format("SELECT MIN(%s), MAX(%s) FROM %s", numericColumn, numericColumn, table);
        if (hasWhereClause()) {
            minMaxSql += " WHERE " + whereClause;
        }

        log.debug("Executing partitioner query: {}", minMaxSql);

        return jdbcTemplate.query(minMaxSql, rs -> {
            if (!rs.next()) {
                log.warn("Partitioner found no records in table {}. No partitions created.", table);
                return new HashMap<>();
            }

            long min = rs.getLong(1);
            long max = rs.getLong(2);

            if (rs.wasNull() || min == max) {
                log.warn("MIN/MAX returned null or a single value for table {}. Creating one partition.", table);
                Map<String, ExecutionContext> result = new HashMap<>();
                ExecutionContext value = new ExecutionContext();
                value.putLong("minId", min);
                value.putLong("maxId", max);
                result.put("partition0", value);
                return result;
            }

            log.info("Partitioning column '{}' from {} to {} into {} partitions.", column, min, max, gridSize);

            long totalRecords = max - min + 1;
            long baseSize = totalRecords / gridSize;
            long remaining = totalRecords % gridSize;

            Map<String, ExecutionContext> result = new HashMap<>();
            long currentId = min;

            for (int i = 0; i < gridSize; i++) {
                long partitionSize = baseSize + (i < remaining ? 1 : 0);

                if (partitionSize <= 0) {
                    continue;
                }

                ExecutionContext value = new ExecutionContext();
                long startId = currentId;
                long endId = startId + partitionSize - 1;

                value.putLong("minId", startId);
                value.putLong("maxId", endId);
                result.put("partition" + i, value);
                log.info("Created partition {}: minId={}, maxId={}", i, startId, endId);

                currentId = endId + 1;
            }

            return result;
        });
    }

    /**
     * Retorna true somente se todos os valores não nulos da coluna forem inteiros
     * (permitindo varchar numérico como "11" ou "35").
     */
    private boolean isNumericColumn() {
        String sql = String.format(
                "SELECT bool_and(TRIM(%s::text) ~ '^-?[0-9]+$') FROM %s WHERE %s IS NOT NULL",
                column, table, column);
        if (hasWhereClause()) {
            sql += " AND (" + whereClause + ")";
        }

        log.debug("Checking if partition column is numeric: {}", sql);
        Boolean allNumeric = jdbcTemplate.queryForObject(sql, Boolean.class);
        return Boolean.TRUE.equals(allNumeric);
    }

    private Map<String, ExecutionContext> singlePartitionWithoutRange() {
        Map<String, ExecutionContext> result = new HashMap<>();
        result.put("partition0", new ExecutionContext());
        return result;
    }

    private boolean hasWhereClause() {
        return whereClause != null && !whereClause.trim().isEmpty();
    }
}
