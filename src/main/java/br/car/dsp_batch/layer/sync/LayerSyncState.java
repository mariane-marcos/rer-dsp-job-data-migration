package br.car.dsp_batch.layer.sync;

import java.time.Instant;

/**
 * Incremental sync state for a layer in the {@code batch_metadata} database.
 */
public record LayerSyncState(
        String layerKey,
        String sourceTable,
        Instant watermarkUpdatedAt,
        Instant lastSuccessAt,
        Long lastJobExecutionId,
        Instant lastOrphanCheckAt
) {
}
