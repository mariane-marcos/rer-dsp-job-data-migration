package br.car.dsp_batch.layer.service;

import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerChangeDetectionServiceTest {

    private final LayerChangeDetectionService service = new LayerChangeDetectionService();

    @Test
    void normalizeId_ConvertsNumberToString() {
        assertEquals("42", service.normalizeId(42L));
        assertEquals("42", service.normalizeId(42));
    }

    @Test
    void normalizeId_KeepsString() {
        assertEquals("ABC123", service.normalizeId("ABC123"));
    }

    @Test
    void requirePositiveSrid_RejectsInvalidMetadata() {
        LayerTableMetadata metadata = metadataWithSrid(0);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> LayerFeaturePersistenceService.requirePositiveSrid(metadata)
        );
        assertTrue(ex.getMessage().contains("srid"));
    }

    @Test
    void requirePositiveSrid_AcceptsValidMetadata() {
        LayerTableMetadata metadata = metadataWithSrid(4674);
        assertDoesNotThrow(() -> LayerFeaturePersistenceService.requirePositiveSrid(metadata));
    }

    @Test
    void joinAttributeSelectColumns_DoesNotDuplicatePrimaryKey() {
        String columns = service.joinAttributeSelectColumns(
                "id",
                List.of("id", "area_of_interest_id")
        );
        assertEquals("id, area_of_interest_id", columns);
    }

    @Test
    void buildComparisonSql_DoesNotSelectPrimaryKeyTwice() {
        LayerTableMetadata metadata = metadataWithSrid(4674);
        String attributeColumns = service.joinAttributeSelectColumns(
                "id",
                List.of("id", "area_of_interest_id", "notes")
        );

        String sql = service.buildComparisonSql(
                metadata,
                attributeColumns,
                "geom",
                "src.teste",
                "WHERE 1=1"
        );

        assertFalse(sql.contains("SELECT id, id,"));
        assertFalse(sql.contains("CollectionExtract"));
        assertTrue(sql.contains("SELECT id, area_of_interest_id, notes,"));
        assertTrue(sql.contains("ST_Force2D(geom) AS layer_geom_2d"));
        assertTrue(sql.contains("ST_Area(layer_geom_2d)"));
        assertTrue(sql.contains("ST_MakeValid(geom)"));
    }

    @Test
    void buildComparisonSql_UsesStableAliasWhenGeometryColumnHasAnotherName() {
        LayerTableMetadata metadata = metadataWithSrid(4674);
        String attributeColumns = service.joinAttributeSelectColumns(
                "id",
                List.of("id", "area_of_interest_id")
        );

        String sql = service.buildComparisonSql(
                metadata,
                attributeColumns,
                "geometry",
                "dsp.rivers",
                ""
        );

        assertTrue(sql.contains("ST_Force2D(geometry) AS layer_geom_2d"));
        assertTrue(sql.contains("ST_Area(layer_geom_2d)"));
        assertTrue(sql.contains("ST_MakeValid(geometry)"));
        assertFalse(sql.contains("ST_Force2D(geom)"));
    }

    private LayerTableMetadata metadataWithSrid(int srid) {
        return new LayerTableMetadata(
                "dsp_teste",
                "teste",
                new QualifiedTable("src", "teste"),
                new QualifiedTable("dsp", "teste"),
                "id",
                "geom",
                "cod_imovel",
                srid,
                List.of(
                        new ColumnMetadata("id", "int8", null, null, null, false, false),
                        new ColumnMetadata("area_of_interest_id", "varchar", 80, null, null, false, false),
                        new ColumnMetadata("notes", "text", null, null, null, true, false),
                        new ColumnMetadata("geom", "geometry", null, null, null, true, true)
                ),
                List.of(),
                "1=1"
        );
    }
}
