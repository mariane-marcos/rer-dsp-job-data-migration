package br.car.dsp_batch.layer.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayersPropertiesTest {

    @Test
    void validate_AcceptsSourceTableAndAreaOfInterestIdColumn() {
        LayersProperties properties = new LayersProperties();
        LayerConfig layer = new LayerConfig();
        layer.setSourceTable("src_schema.tabela");
        layer.setAreaOfInterestIdColumn("cod_imovel");
        properties.getLayers().add(layer);

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void validate_RejectsMissingSourceTable() {
        LayersProperties properties = new LayersProperties();
        LayerConfig layer = new LayerConfig();
        layer.setLayerName("orphan-layer");
        layer.setAreaOfInterestIdColumn("cod_imovel");
        properties.getLayers().add(layer);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertTrue(ex.getMessage().contains("source-table"));
    }

    @Test
    void validate_RejectsMissingAreaOfInterestIdColumn() {
        LayersProperties properties = new LayersProperties();
        LayerConfig layer = new LayerConfig();
        layer.setSourceTable("src_schema.tabela");
        properties.getLayers().add(layer);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertTrue(ex.getMessage().contains("area-of-interest-id-column"));
    }

    @Test
    void validate_RejectsDuplicateTargetTableNames() {
        LayersProperties properties = new LayersProperties();

        LayerConfig first = new LayerConfig();
        first.setSourceTable("schema_a.mesma");
        first.setAreaOfInterestIdColumn("cod_imovel");

        LayerConfig second = new LayerConfig();
        second.setSourceTable("schema_b.mesma");
        second.setAreaOfInterestIdColumn("cod_imovel");

        properties.getLayers().add(first);
        properties.getLayers().add(second);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertTrue(ex.getMessage().contains("Duplicate target table"));
    }
}
