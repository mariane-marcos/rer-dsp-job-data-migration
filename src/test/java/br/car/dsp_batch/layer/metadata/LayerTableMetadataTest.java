package br.car.dsp_batch.layer.metadata;

import br.car.dsp_batch.layer.config.LayerConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayerTableMetadataTest {

    @Test
    void resolveTargetGeometryColumn_AlwaysReturnsGeom() {
        LayerTableMetadata metadata = sampleMetadata("the_geom", "data_atualizacao");

        assertEquals(LayerConfig.GEOMETRY_COLUMN, metadata.resolveTargetGeometryColumn());
        assertEquals("geom", metadata.resolveTargetColumnName("the_geom"));
        assertEquals("the_geom", metadata.resolveSourceColumnName("geom"));
    }

    @Test
    void resolveTargetColumnName_KeepsGeomWhenSourceAlreadyCanonical() {
        LayerTableMetadata metadata = sampleMetadata("geom", "updated_at");

        assertEquals("geom", metadata.resolveTargetColumnName("geom"));
        assertEquals("geom", metadata.resolveSourceColumnName("geom"));
    }

    @Test
    void resolveTargetColumnName_StillRenamesAreaOfInterestId() {
        LayerTableMetadata metadata = sampleMetadata("the_geom", "data_atualizacao");

        assertEquals("area_of_interest_id", metadata.resolveTargetColumnName("conservation_unit_id"));
        assertEquals("conservation_unit_id", metadata.resolveSourceColumnName("area_of_interest_id"));
    }

    @Test
    void resolveTargetColumnName_RenamesUpdatedAtLikeGeometry() {
        LayerTableMetadata metadata = sampleMetadata("the_geom", "data_atualizacao");

        assertEquals(LayerConfig.UPDATED_AT_COLUMN, metadata.resolveTargetUpdatedAtColumn());
        assertEquals("updated_at", metadata.resolveTargetColumnName("data_atualizacao"));
        assertEquals("data_atualizacao", metadata.resolveSourceColumnName("updated_at"));
    }

    @Test
    void resolveTargetColumnName_KeepsUpdatedAtWhenSourceAlreadyCanonical() {
        LayerTableMetadata metadata = sampleMetadata("the_geom", "updated_at");

        assertEquals("updated_at", metadata.resolveTargetColumnName("updated_at"));
        assertEquals("updated_at", metadata.resolveSourceColumnName("updated_at"));
    }

    private LayerTableMetadata sampleMetadata(String geometryColumn, String updatedAtColumn) {
        return new LayerTableMetadata(
                "dsp_rivers",
                "rivers",
                new QualifiedTable("conservation", "rivers"),
                new QualifiedTable("dsp", "rivers"),
                "id",
                geometryColumn,
                "conservation_unit_id",
                updatedAtColumn,
                4674,
                List.of(
                        new ColumnMetadata("id", "int8", null, null, null, false, false),
                        new ColumnMetadata("conservation_unit_id", "int8", null, null, null, false, false),
                        new ColumnMetadata(updatedAtColumn, "timestamptz", null, null, null, false, false),
                        new ColumnMetadata(geometryColumn, "geometry", null, null, null, true, true)
                ),
                List.of(),
                "1=1"
        );
    }
}
