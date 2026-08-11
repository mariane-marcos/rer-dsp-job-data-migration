package br.car.dsp_batch.batch.config.strategy;

import br.car.dsp_batch.batch.config.table.AdministrativeUnitTableProperties;
import br.car.dsp_batch.batch.config.table.AreaOfInterestTableProperties;
import br.car.dsp_batch.sync.SyncKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatermarkChangeDetectionStrategyTest {

    @Test
    void requireUpdatedAtColumn_RejectsMissingColumn() {
        AdministrativeUnitTableProperties config = new AdministrativeUnitTableProperties();
        config.setSourceTable("source.unit");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> WatermarkChangeDetectionStrategy.requireUpdatedAtColumn(config)
        );
        assertTrue(ex.getMessage().contains("updated-at-column"));
    }

    @Test
    void requireSyncKey_RejectsMissingKey() {
        AdministrativeUnitTableProperties config = new AdministrativeUnitTableProperties();
        config.setSourceTable("source.unit");
        config.setUpdatedAtColumn("source_updated_at");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> WatermarkChangeDetectionStrategy.requireSyncKey(config)
        );
        assertTrue(ex.getMessage().contains("sync-key"));
    }

    @Test
    void areaOfInterestProperties_DefaultSyncKey() {
        AreaOfInterestTableProperties config = new AreaOfInterestTableProperties();
        assertEquals(SyncKeys.AREA_OF_INTEREST, config.getSyncKey());
    }

    @Test
    void areaOfInterestProperties_ValidateRequiresUpdatedAtForWatermark() {
        AreaOfInterestTableProperties config = new AreaOfInterestTableProperties();
        config.setChangeDetectionStrategy(ChangeDetectionStrategyType.WATERMARK);

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(ex.getMessage().contains("updated-at-column"));
    }
}
