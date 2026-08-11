package br.car.dsp_batch.sync;

import java.time.Instant;

/**
 * Incremental sync state for a job entity in the {@code batch_metadata} database.
 */
public record SyncState(
        String syncKey,
        String sourceTable,
        Instant watermarkUpdatedAt,
        Instant lastSuccessAt,
        Long lastJobExecutionId,
        Instant lastOrphanCheckAt
) {
}
