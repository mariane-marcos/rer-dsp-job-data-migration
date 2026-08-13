package br.car.dsp_batch.sync;

/**
 * Neutral table descriptor for incremental watermark change detection.
 * Used by admin units/AOI and by layers.
 */
public record WatermarkTableSpec(
        String syncKey,
        String sourceTable,
        String sourcePrimaryKey,
        String sourceGeometryColumn,
        String sourceUpdatedAtColumn,
        String whereClause,
        int srid,
        String layerName,
        String geoTargetTable,
        String geoTargetPrimaryKey,
        String geoTargetGeometryColumn,
        /** When null, orphans are removed only from the geo-target. */
        String businessTargetTable,
        String businessTargetPrimaryKey
) {
}
