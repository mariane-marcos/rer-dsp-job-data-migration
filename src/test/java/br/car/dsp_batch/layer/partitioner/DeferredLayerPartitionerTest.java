package br.car.dsp_batch.layer.partitioner;

import br.car.dsp_batch.sync.WatermarkSql;
import br.car.dsp_batch.temporal.TemporalTestFixtures;
import br.car.dsp_batch.temporal.WatermarkTemporalBridge;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredLayerPartitionerTest {

    @Test
    void combineWhere_ReturnsNullWhenNothingToFilter() {
        assertNull(WatermarkSql.combineWhere("1=1", null));
        assertNull(WatermarkSql.combineWhere(null, null));
    }

    @Test
    void combineWhere_KeepsOnlyConfigWhere() {
        assertEquals("status = 'A'", WatermarkSql.combineWhere("status = 'A'", null));
    }

    @Test
    void combineWhere_KeepsOnlyUpdatedAtFilter() {
        assertEquals(
                "data_atualizacao IS NOT NULL",
                WatermarkSql.combineWhere("1=1", "data_atualizacao IS NOT NULL")
        );
    }

    @Test
    void combineWhere_CombinesBridgePredicate() {
        Instant wm = Instant.parse("2026-08-10T15:00:00Z");
        String filter = WatermarkSql.buildUpdatedAtFilter(
                TemporalTestFixtures.timestamptz("data_atualizacao"), wm);
        assertEquals(
                "(status = 'A') AND " + filter,
                WatermarkSql.combineWhere("status = 'A'", filter)
        );
        assertTrue(filter.contains("TIMESTAMP WITH TIME ZONE '2026-08-10T15:00:00Z'"));
        assertEquals(
                filter,
                WatermarkTemporalBridge.buildPredicate(
                        TemporalTestFixtures.timestamptz("data_atualizacao"), wm).sqlFragment()
        );
    }
}
