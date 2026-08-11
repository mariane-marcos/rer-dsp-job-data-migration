package br.car.dsp_batch.layer.listener;

import br.car.dsp_batch.layer.service.LayerChangeDetectionService;
import br.car.dsp_batch.layer.sync.LayerSyncStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Advances the layer watermark only when the job finishes successfully.
 */
@Slf4j
@Component
public class LayerWatermarkCommitListener implements JobExecutionListener {

    private final LayerSyncStateRepository syncStateRepository;

    public LayerWatermarkCommitListener(LayerSyncStateRepository syncStateRepository) {
        this.syncStateRepository = syncStateRepository;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            log.warn(
                    "Job {} finished with status={} — watermark will not advance",
                    jobExecution.getJobInstance().getJobName(),
                    jobExecution.getStatus()
            );
            return;
        }

        String layerKey = jobExecution.getJobParameters().getString("layerKey");
        if (layerKey == null || layerKey.isBlank()) {
            layerKey = jobExecution.getExecutionContext().getString("layerKey", null);
        }
        if (layerKey == null || layerKey.isBlank()) {
            log.error("Cannot advance watermark: layerKey missing from job {}",
                    jobExecution.getJobInstance().getJobName());
            return;
        }

        String sourceTable = jobExecution.getExecutionContext().getString("sourceTable", layerKey);
        String proposed = jobExecution.getExecutionContext()
                .getString(LayerChangeDetectionService.CTX_PROPOSED_WATERMARK, null);
        Instant watermark = proposed != null ? Instant.parse(proposed) : null;

        Boolean orphanCheckRan = (Boolean) jobExecution.getExecutionContext()
                .get(LayerChangeDetectionService.CTX_ORPHAN_CHECK_RAN);

        if (watermark != null) {
            syncStateRepository.advanceWatermark(
                    layerKey,
                    sourceTable,
                    watermark,
                    jobExecution.getId()
            );
            log.info("Watermark advanced for layerKey={} to {}", layerKey, watermark);
        }

        if (Boolean.TRUE.equals(orphanCheckRan)) {
            syncStateRepository.markOrphanCheck(layerKey, sourceTable);
            log.info("Orphan check timestamp updated for layerKey={}", layerKey);
        }
    }
}
