package br.car.dsp_batch.layer.introspection;

import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaIntrospectionServiceTest {

    private final SchemaIntrospectionService service = new SchemaIntrospectionService();
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
    }

    @Test
    void introspect_UsesFirstGeometryWhenMultipleAndNoOverride() {
        LayerConfig config = baseConfig();
        config.setSrid(4674);

        stubTableExists();
        stubColumns(
                column("id", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("data_atualizacao", "timestamptz", false),
                column("centroid", "geometry", true),
                column("the_geom", "geometry", true)
        );
        stubPrimaryKey("id");
        stubGeometryColumns(
                geomColumn("centroid", 4674, "POINT"),
                geomColumn("the_geom", 4674, "MULTILINESTRING")
        );
        stubEmptyIndexes();

        LayerTableMetadata metadata = service.introspect(jdbc, config);

        assertEquals("centroid", metadata.geometryColumn());
        assertEquals("geom", metadata.resolveTargetGeometryColumn());
        assertEquals("data_atualizacao", metadata.updatedAtSourceColumn());
        assertEquals("updated_at", metadata.resolveTargetUpdatedAtColumn());
    }

    @Test
    void introspect_HonorsGeometryColumnOverrideAndMapsToGeom() {
        LayerConfig config = baseConfig();
        config.setGeometryColumn("the_geom");
        config.setSrid(4674);

        stubTableExists();
        stubColumns(
                column("id", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("data_atualizacao", "timestamptz", false),
                column("centroid", "geometry", true),
                column("the_geom", "geometry", true)
        );
        stubPrimaryKey("id");
        stubGeometryColumnLookup("the_geom", 4674, "MULTILINESTRING");
        stubEmptyIndexes();

        LayerTableMetadata metadata = service.introspect(jdbc, config);

        assertEquals("the_geom", metadata.geometryColumn());
        assertEquals("geom", metadata.resolveTargetGeometryColumn());
        assertEquals(4674, metadata.srid());
    }

    @Test
    void introspect_UsesConfiguredSridOverSource() {
        LayerConfig config = baseConfig();
        config.setGeometryColumn("the_geom");
        config.setSrid(4326);

        stubTableExists();
        stubColumns(
                column("id", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("data_atualizacao", "timestamptz", false),
                column("the_geom", "geometry", true)
        );
        stubPrimaryKey("id");
        stubGeometryColumnLookup("the_geom", 4674, "MULTILINESTRING");
        stubEmptyIndexes();

        LayerTableMetadata metadata = service.introspect(jdbc, config);

        assertEquals(4326, metadata.srid());
    }

    @Test
    void introspect_FailsWhenGeometryColumnIsNotGeometryType() {
        LayerConfig config = baseConfig();
        config.setGeometryColumn("name");

        stubTableExists();
        stubColumns(
                column("id", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("data_atualizacao", "timestamptz", false),
                column("name", "varchar", false),
                column("the_geom", "geometry", true)
        );
        stubPrimaryKey("id");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.introspect(jdbc, config)
        );

        assertTrue(ex.getMessage().contains("is not geometry/geography"));
        assertTrue(ex.getMessage().contains("geometry-column"));
    }

    @Test
    void introspect_FailsWithoutSrid() {
        LayerConfig config = baseConfig();
        config.setGeometryColumn("the_geom");

        stubTableExists();
        stubColumns(
                column("id", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("data_atualizacao", "timestamptz", false),
                column("the_geom", "geometry", true)
        );
        stubPrimaryKey("id");
        stubGeometryColumnLookup("the_geom", 0, "GEOMETRY");
        stubSampledSrid(0);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.introspect(jdbc, config)
        );

        assertTrue(ex.getMessage().contains("SRID not found"));
        assertTrue(ex.getMessage().contains("batch.layers.srid"));
    }

    @Test
    void introspect_FailsWhenUpdatedAtColumnMissing() {
        LayerConfig config = baseConfig();
        config.setUpdatedAtColumn("data_inexistente");
        config.setSrid(4674);

        stubTableExists();
        stubColumns(
                column("id", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("data_atualizacao", "timestamptz", false),
                column("the_geom", "geometry", true)
        );
        stubPrimaryKey("id");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.introspect(jdbc, config)
        );

        assertTrue(ex.getMessage().contains("updated-at-column"));
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    void introspect_FailsWhenUpdatedAtColumnHasInvalidType() {
        LayerConfig config = baseConfig();
        config.setUpdatedAtColumn("name");
        config.setSrid(4674);

        stubTableExists();
        stubColumns(
                column("id", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("name", "varchar", false),
                column("the_geom", "geometry", true)
        );
        stubPrimaryKey("id");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.introspect(jdbc, config)
        );

        assertTrue(ex.getMessage().contains("updated-at-column"));
        assertTrue(ex.getMessage().contains("timestamp"));
    }

    private LayerConfig baseConfig() {
        LayerConfig config = new LayerConfig();
        config.setSourceTable("conservation.rivers");
        config.setAreaOfInterestIdColumn("conservation_unit_id");
        config.setUpdatedAtColumn("data_atualizacao");
        return config;
    }

    private void stubTableExists() {
        when(jdbc.queryForObject(contains("information_schema.tables"), eq(Integer.class), any(), any()))
                .thenReturn(1);
    }

    private void stubColumns(ColumnMetadata... columns) {
        when(jdbc.query(contains("information_schema.columns"), any(RowMapper.class), any(), any()))
                .thenReturn(List.of(columns));
    }

    private void stubPrimaryKey(String column) {
        when(jdbc.query(contains("PRIMARY KEY"), any(RowMapper.class), any(), any()))
                .thenReturn(List.of(column));
    }

    private void stubEmptyIndexes() {
        when(jdbc.query(contains("pg_indexes"), any(RowMapper.class), any(), any()))
                .thenReturn(List.of());
    }

    private void stubGeometryColumns(GeomColumn... rows) {
        when(jdbc.query(contains("geometry_columns"), any(RowMapper.class), any(), any()))
                .thenAnswer(invocation -> mapGeometryRows(invocation.getArgument(1), rows));
    }

    private void stubGeometryColumnLookup(String columnName, Integer srid, String type) {
        when(jdbc.query(contains("f_geometry_column = ?"), any(RowMapper.class), any(), any(), eq(columnName)))
                .thenAnswer(invocation -> mapGeometryRows(
                        invocation.getArgument(1),
                        geomColumn(columnName, srid, type)
                ));
    }

    private void stubSampledSrid(int srid) {
        when(jdbc.query(contains("ST_SRID"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .thenReturn(srid);
    }

    @SuppressWarnings("unchecked")
    private List<Object> mapGeometryRows(RowMapper<Object> mapper, GeomColumn... rows) {
        List<Object> result = new ArrayList<>();
        int rowNum = 0;
        for (GeomColumn row : rows) {
            ResultSet rs = mock(ResultSet.class);
            try {
                when(rs.getString("f_geometry_column")).thenReturn(row.columnName());
                when(rs.getObject("srid")).thenReturn(row.srid());
                when(rs.getString("type")).thenReturn(row.type());
                result.add(mapper.mapRow(rs, rowNum++));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private static ColumnMetadata column(String name, String udt, boolean geometry) {
        return new ColumnMetadata(name, udt, null, null, null, true, geometry);
    }

    private static GeomColumn geomColumn(String name, Integer srid, String type) {
        return new GeomColumn(name, srid, type);
    }

    private record GeomColumn(String columnName, Integer srid, String type) {
    }
}
