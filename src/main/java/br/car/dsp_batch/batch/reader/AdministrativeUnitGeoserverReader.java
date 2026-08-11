package br.car.dsp_batch.batch.reader;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import br.car.dsp_batch.geometry.GeometrySql;
import br.car.dsp_batch.sync.WatermarkSql;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic paging reader driven by {@link JobTableConfig}.
 */
public class AdministrativeUnitGeoserverReader
        extends AbstractPartitionedPagingItemReader<AdministrativeUnitDTO> {

    public AdministrativeUnitGeoserverReader(DataSource dataSource,
                                             Long minId,
                                             Long maxId,
                                             int pageSize,
                                             JobTableConfig tableConfig,
                                             String readerName) {
        this(dataSource, minId, maxId, pageSize, tableConfig, null, null, readerName);
    }

    public AdministrativeUnitGeoserverReader(DataSource dataSource,
                                             Long minId,
                                             Long maxId,
                                             int pageSize,
                                             JobTableConfig tableConfig,
                                             Instant watermark,
                                             String updatedAtColumn,
                                             String readerName) {
        super(dataSource, minId, maxId, pageSize);
        this.setName(readerName);
        boolean useIdRange = minId != null && maxId != null;
        this.setQueryProvider(createQueryProvider(tableConfig, useIdRange, watermark, updatedAtColumn));
        this.setRowMapper(new AdministrativeUnitRowMapper(tableConfig));
    }

    private PagingQueryProvider createQueryProvider(JobTableConfig tableConfig,
                                                    boolean useIdRange,
                                                    Instant watermark,
                                                    String updatedAtColumn) {
        String partitionColumn = tableConfig.getPartitionColumn();
        String geom = tableConfig.getGeometryColumn();
        List<String> persistColumns = new ArrayList<>(tableConfig.getAllBusinessPersistColumns());

        String selectColumns = String.join(", ", persistColumns);

        // Ensures the partition column is included in the SELECT clause,
        // even if it is not listed in persist-columns.
        if (!persistColumns.contains(partitionColumn)) {
            selectColumns = selectColumns + ", " + partitionColumn;
        }

        int srid = tableConfig.getSrid();
        String transformedGeom = "public.ST_Transform(" + geom + ", " + srid + ")";

        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause(
                "SELECT " + selectColumns
                        + ", " + GeometrySql.asGeoJsonText2d(transformedGeom)
                        + " AS geometry_geo_json"
        );
        queryProvider.setFromClause("FROM " + tableConfig.getSourceTable());

        String geometryFilter = GeometrySql.validNonEmptyPredicate(geom);

        // YAML where-clause (sample / adopter filters) must also apply on the write read.
        String configWhere = tableConfig.getWhereClause();
        boolean hasConfigWhere = configWhere != null
                && !configWhere.isBlank()
                && !"1=1".equals(configWhere.trim());

        StringBuilder where = new StringBuilder("WHERE ").append(geometryFilter);
        if (hasConfigWhere) {
            where.append(" AND (").append(configWhere).append(")");
        }

        if (updatedAtColumn != null && !updatedAtColumn.isBlank()) {
            String updatedAtFilter = WatermarkSql.buildUpdatedAtFilter(updatedAtColumn.trim(), watermark);
            where.append(" AND ").append(updatedAtFilter);
        }

        if (useIdRange) {
            // CAST handles VARCHAR columns containing numeric values (e.g., cd_uf);
            // minId/maxId are Long values.
            String numericPartitionColumn = "CAST(" + partitionColumn + " AS BIGINT)";
            where.append(" AND ").append(numericPartitionColumn).append(" >= :minId")
                    .append(" AND ").append(numericPartitionColumn).append(" <= :maxId");
        }

        queryProvider.setWhereClause(where.toString());

        Map<String, Order> sortKeys = new HashMap<>();
        sortKeys.put(partitionColumn, Order.ASCENDING);
        queryProvider.setSortKeys(sortKeys);

        return queryProvider;
    }

    private static class AdministrativeUnitRowMapper implements RowMapper<AdministrativeUnitDTO> {

        private final JobTableConfig tableConfig;

        AdministrativeUnitRowMapper(JobTableConfig tableConfig) {
            this.tableConfig = tableConfig;
        }

        @Override
        public AdministrativeUnitDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            AdministrativeUnitDTO dto = new AdministrativeUnitDTO();
            dto.setId(rs.getObject(tableConfig.getPrimaryKey()));
            dto.setGeometryGeoJson(rs.getString("geometry_geo_json"));

            for (String column : tableConfig.getAllBusinessPersistColumns()) {
                dto.putAttribute(column, rs.getObject(column));
            }

            return dto;
        }
    }
}
