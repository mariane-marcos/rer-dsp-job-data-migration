package br.car.dsp_batch.sync;

import br.car.dsp_batch.batch.config.table.AdministrativeUnitTableProperties;
import br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategyType;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatermarkTableSpecsTest {

    @Test
    void fromJobTableConfig_MapsAdminUnitFields() {
        AdministrativeUnitTableProperties config = new AdministrativeUnitTableProperties();
        config.setSourceTable("source.l1");
        config.setTargetTable("target.l1");
        config.setPrimaryKey("source_pk");
        config.setGeometryColumn("source_geom");
        config.setUpdatedAtColumn("source_updated_at");
        config.setSyncKey(SyncKeys.ADMIN_UNIT_LEVEL_1);
        config.setWhereClause("1=1");
        config.setSrid(4326);
        config.setLayerName("l1-layer");
        config.setChangeDetectionStrategy(ChangeDetectionStrategyType.WATERMARK);
        config.getColumnMapping().put("source_pk", "target_id");
        config.getColumnMapping().put("source_geom", "target_geom");

        WatermarkTableSpec spec = WatermarkTableSpecs.fromJobTableConfig(config);

        assertEquals(SyncKeys.ADMIN_UNIT_LEVEL_1, spec.syncKey());
        assertEquals("source_updated_at", spec.sourceUpdatedAtColumn());
        assertEquals("target.l1", spec.geoTargetTable());
        assertEquals("target.l1", spec.businessTargetTable());
        assertEquals("target_id", spec.geoTargetPrimaryKey());
        assertEquals("target_geom", spec.geoTargetGeometryColumn());
    }

    @Test
    void fromJobTableConfig_RequiresUpdatedAt() {
        AdministrativeUnitTableProperties config = new AdministrativeUnitTableProperties();
        config.setSourceTable("source.l1");
        config.setSyncKey(SyncKeys.ADMIN_UNIT_LEVEL_1);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> WatermarkTableSpecs.fromJobTableConfig(config)
        );
        assertTrue(ex.getMessage().contains("updated-at-column"));
    }

    @Test
    void fromLayerMetadata_OmitsBusinessTarget() {
        LayerTableMetadata metadata = new LayerTableMetadata(
                "dsp_parcelas",
                "parcelas",
                new QualifiedTable("src", "parcelas"),
                new QualifiedTable("dsp", "parcelas"),
                "id",
                "geom",
                "aoi_fk",
                "updated_at",
                "nome",
                4326,
                List.of(new ColumnMetadata("id", "int8", null, null, null, false, false)),
                List.of(),
                "1=1"
        );

        WatermarkTableSpec spec = WatermarkTableSpecs.fromLayerMetadata(metadata);

        assertEquals("dsp_parcelas", spec.syncKey());
        assertEquals("dsp.parcelas", spec.geoTargetTable());
        assertNull(spec.businessTargetTable());
    }
}
