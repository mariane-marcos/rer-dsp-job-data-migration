package br.car.dsp_batch.layer.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayerConfigTest {

    @Test
    void resolveTargetTable_UsesDspSchemaAndSourceTableName() {
        LayerConfig config = new LayerConfig();
        config.setSourceTable("usr_geocar_aplicacao.parcelas_rurais");

        assertEquals("dsp", config.resolveTargetTable().schema());
        assertEquals("parcelas_rurais", config.resolveTargetTable().table());
        assertEquals("dsp.parcelas_rurais", config.resolveTargetTable().qualified());
    }

    @Test
    void resolveKey_DerivesFromTargetTableName() {
        LayerConfig config = new LayerConfig();
        config.setSourceTable("source_schema.example_features");

        assertEquals("dsp_example_features", config.resolveKey());
    }

    @Test
    void resolveLayerName_DefaultsToSourceTableName() {
        LayerConfig config = new LayerConfig();
        config.setSourceTable("cliente.zona_app");

        assertEquals("zona_app", config.resolveLayerName());
    }

    @Test
    void areaOfInterestIdColumn_ConstantMatchesTargetColumnName() {
        assertEquals("area_of_interest_id", LayerConfig.AREA_OF_INTEREST_ID_COLUMN);
    }

    @Test
    void geometryColumn_ConstantMatchesTargetColumnName() {
        assertEquals("geom", LayerConfig.GEOMETRY_COLUMN);
    }

    @Test
    void updatedAtColumn_ConstantMatchesTargetColumnName() {
        assertEquals("updated_at", LayerConfig.UPDATED_AT_COLUMN);
    }
}
