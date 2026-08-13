package br.car.dsp_batch.sync;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;

/**
 * Factories that build {@link WatermarkTableSpec} from domain contracts.
 */
public final class WatermarkTableSpecs {

    private WatermarkTableSpecs() {
    }

    public static WatermarkTableSpec fromJobTableConfig(JobTableConfig tableConfig) {
        String syncKey = tableConfig.getSyncKey();
        if (syncKey == null || syncKey.isBlank()) {
            throw new IllegalArgumentException(
                    "WATERMARK strategy requires sync-key for table "
                            + tableConfig.getSourceTable());
        }
        String updatedAt = tableConfig.getUpdatedAtColumn();
        if (updatedAt == null || updatedAt.isBlank()) {
            throw new IllegalArgumentException(
                    "WATERMARK strategy requires updated-at-column for table "
                            + tableConfig.getSourceTable());
        }

        String targetPk = tableConfig.resolveTargetColumn(tableConfig.getPrimaryKey());
        String targetGeom = tableConfig.resolveTargetColumn(tableConfig.getGeometryColumn());

        return new WatermarkTableSpec(
                syncKey.trim(),
                tableConfig.getSourceTable(),
                tableConfig.getPrimaryKey(),
                tableConfig.getGeometryColumn(),
                updatedAt.trim(),
                tableConfig.getWhereClause(),
                tableConfig.getSrid(),
                tableConfig.getLayerName(),
                tableConfig.getTargetTable(),
                targetPk,
                targetGeom,
                tableConfig.getTargetTable(),
                targetPk
        );
    }

    /**
     * For layers: orphan cleanup runs only on geo-target ({@code businessTarget} is null).
     * Admin-unit jobs use the same logical table name on business and geo databases;
     * layers write only to {@code dsp.*} on geo-target.
     */
    public static WatermarkTableSpec fromLayerMetadata(LayerTableMetadata metadata) {
        return new WatermarkTableSpec(
                metadata.layerKey(),
                metadata.qualifiedSourceTable(),
                metadata.primaryKeyColumn(),
                metadata.geometryColumn(),
                metadata.updatedAtSourceColumn(),
                metadata.whereClause(),
                metadata.srid(),
                metadata.layerName(),
                metadata.qualifiedTargetTable(),
                metadata.resolveTargetPrimaryKeyColumn(),
                metadata.resolveTargetGeometryColumn(),
                null,
                null
        );
    }
}
