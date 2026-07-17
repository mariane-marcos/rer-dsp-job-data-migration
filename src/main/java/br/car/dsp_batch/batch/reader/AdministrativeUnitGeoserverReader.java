package br.car.dsp_batch.batch.reader;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        super(dataSource, minId, maxId, pageSize);
        this.setName(readerName);
        this.setQueryProvider(createQueryProvider(tableConfig));
        this.setRowMapper(new AdministrativeUnitRowMapper(tableConfig));
    }

    private PagingQueryProvider createQueryProvider(JobTableConfig tableConfig) {
        String partitionColumn = tableConfig.getPartitionColumn();
        String geom = tableConfig.getGeometryColumn();
        List<String> persistColumns = tableConfig.getPersistColumns();

        String selectColumns = String.join(", ", persistColumns);

        // Ensure partition column is selected even if not in persist columns
        if (!persistColumns.contains(partitionColumn)) {
            selectColumns = selectColumns + ", " + partitionColumn;
        }

        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause(
                "SELECT " + selectColumns
                        + ", public.ST_AsGeoJSON(" + geom + ")::text AS geometry_geo_json"
        );
        queryProvider.setFromClause("FROM " + tableConfig.getSourceTable());
        // CAST evita erro quando a coluna de partição é varchar com valores numéricos
        // (ex.: cd_uf), já que minId/maxId são Long.
        String numericPartitionColumn = "CAST(" + partitionColumn + " AS BIGINT)";
        queryProvider.setWhereClause(
                "WHERE " + numericPartitionColumn + " >= :minId AND " + numericPartitionColumn + " <= :maxId"
                        + " AND " + geom + " IS NOT NULL"
                        + " AND NOT ST_IsEmpty(ST_Multi(ST_CollectionExtract("
                        + "ST_MakeValid(COALESCE(" + geom + ", ST_Buffer(" + geom + ", 0))), 3)))"
        );

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

            for (String column : tableConfig.getPersistColumns()) {
                dto.putAttribute(column, rs.getObject(column));
            }

            return dto;
        }
    }
}
