package br.car.dsp_batch.batch.config;

/**
 * Validates watermark-related fields on {@link JobTableConfig}.
 */
public final class JobTableConfigValidator {

    private JobTableConfigValidator() {
    }

    public static void requireWatermarkFields(JobTableConfig tableConfig) {
        requireUpdatedAtColumn(tableConfig);
        requireSyncKey(tableConfig);
    }

    public static void requireUpdatedAtColumn(JobTableConfig tableConfig) {
        String column = tableConfig.getUpdatedAtColumn();
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException(
                    "updated-at-column is required for table " + tableConfig.getSourceTable());
        }
    }

    public static String requireSyncKey(JobTableConfig tableConfig) {
        String syncKey = tableConfig.getSyncKey();
        if (syncKey == null || syncKey.isBlank()) {
            throw new IllegalArgumentException(
                    "sync-key is required for table " + tableConfig.getSourceTable());
        }
        return syncKey.trim();
    }
}
