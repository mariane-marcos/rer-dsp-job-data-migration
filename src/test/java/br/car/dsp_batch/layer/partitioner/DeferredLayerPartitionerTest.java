package br.car.dsp_batch.layer.partitioner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeferredLayerPartitionerTest {

    @Test
    void combineWhere_ReturnsNullWhenNothingToFilter() {
        assertNull(DeferredLayerPartitioner.combineWhere("1=1", null));
        assertNull(DeferredLayerPartitioner.combineWhere(null, null));
    }

    @Test
    void combineWhere_KeepsOnlyConfigWhere() {
        assertEquals("status = 'A'", DeferredLayerPartitioner.combineWhere("status = 'A'", null));
    }

    @Test
    void combineWhere_KeepsOnlyUpdatedAtFilter() {
        assertEquals(
                "data_atualizacao IS NOT NULL",
                DeferredLayerPartitioner.combineWhere("1=1", "data_atualizacao IS NOT NULL")
        );
    }

    @Test
    void combineWhere_CombinesBoth() {
        assertEquals(
                "(status = 'A') AND data_atualizacao IS NOT NULL AND data_atualizacao > TIMESTAMP WITH TIME ZONE '2026-08-10T15:00:00Z'",
                DeferredLayerPartitioner.combineWhere(
                        "status = 'A'",
                        "data_atualizacao IS NOT NULL AND data_atualizacao > TIMESTAMP WITH TIME ZONE '2026-08-10T15:00:00Z'")
        );
    }
}
