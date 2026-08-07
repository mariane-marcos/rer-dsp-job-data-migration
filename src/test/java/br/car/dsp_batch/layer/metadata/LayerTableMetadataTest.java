package br.car.dsp_batch.layer.metadata;

import br.car.dsp_batch.layer.config.LayerConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayerTableMetadataTest {

    @Test
    void resolveTargetGeometryColumn_AlwaysReturnsGeom() {
        LayerTableMetadata metadata = sampleMetadata("the_geom");

        assertEquals(LayerConfig.GEOMETRY_COLUMN, metadata.resolveTargetGeometryColumn());
        assertEquals("geom", metadata.resolveTargetColumnName("the_geom"));
        assertEquals("the_geom", metadata.resolveSourceColumnName("geom"));
    }

    @Test
    void resolveTargetColumnName_KeepsGeomWhenSourceAlreadyCanonical() {
        LayerTableMetadata metadata = sampleMetadata("geom");

        assertEquals("geom", metadata.resolveTargetColumnName("geom"));
        assertEquals("geom", metadata.resolveSourceColumnName("geom"));
    }

    @Test
    void resolveTargetColumnName_StillRenamesAreaOfInterestId() {
        LayerTableMetadata metadata = sampleMetadata("the_geom");

        assertEquals("area_of_interest_id", metadata.resolveTargetColumnName("conservation_unit_id"));
        assertEquals("conservation_unit_id", metadata.resolveSourceColumnName("area_of_interest_id"));
    }

    private LayerTableMetadata sampleMetadata(String geometryColumn) {
        return new LayerTableMetadata(
                "dsp_rivers",
                "rivers",
                new QualifiedTable("conservation", "rivers"),
                new QualifiedTable("dsp", "rivers"),
                "id",
                geometryColumn,
                "conservation_unit_id",
                4674,
                List.of(
                        new ColumnMetadata("id", "int8", null, null, null, false, false),
                        new ColumnMetadata("conservation_unit_id", "int8", null, null, null, false, false),
                        new ColumnMetadata(geometryColumn, "geometry", null, null, null, true, true)
                ),
                List.of(),
                "1=1"
        );
    }
}
