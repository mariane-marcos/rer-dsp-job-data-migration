package br.car.dsp_batch.batch.config;

import br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategyType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration contract for geographic administrative unit table synchronization.
 * Concrete implementations supply table-specific values (typically externalized).
 */
public interface JobTableConfig {

    /** Fully qualified source table name (schema.table). */
    String getSourceTable();

    /** Fully qualified target table name (schema.table). */
    String getTargetTable();

    /** Primary key column name on the source table. */
    String getPrimaryKey();

    /**
     * Column used for range partitioning.
     * Defaults to the primary key when not explicitly set.
     */
    default String getPartitionColumn() {
        return getPrimaryKey();
    }

    /** Geometry column name on the source table. */
    String getGeometryColumn();

    /** WHERE clause fragment used when reading active source records (without the WHERE keyword). */
    String getWhereClause();

    /** Non-geometry columns compared during change detection. */
    List<String> getComparisonColumns();

    /**
     * Columns persisted on both business and geo targets (excluding geometry).
     * Must include the primary key column.
     */
    List<String> getPersistColumns();

    /**
     * Extra columns written only to the business target ({@code dsp-db}).
     * Use for KPI measure columns (e.g. {@code theme_1}) that must not go to the exhibition DB.
     */
    default List<String> getBusinessOnlyPersistColumns() {
        return Collections.emptyList();
    }

    /**
     * {@link #getPersistColumns()} plus {@link #getBusinessOnlyPersistColumns()} (no duplicates).
     */
    default List<String> getAllBusinessPersistColumns() {
        List<String> merged = new ArrayList<>(getPersistColumns());
        List<String> businessOnly = getBusinessOnlyPersistColumns();
        if (businessOnly != null) {
            for (String column : businessOnly) {
                if (column != null && !column.isBlank() && !merged.contains(column)) {
                    merged.add(column);
                }
            }
        }
        return merged;
    }

    /** GeoServer / GeoWebCache layer name. */
    String getLayerName();

    /**
     * Maps source column names to target column names.
     * Empty map means source and target share the same names.
     */
    default Map<String, String> getColumnMapping() {
        return Collections.emptyMap();
    }

    /**
     * Geometry SRID configured per job in YAML (required; must be a positive integer).
     * No application-wide default — each adopter sets the value for their coordinate system.
     */
    int getSrid();

    /** Change detection strategy to use for this job. */
    default ChangeDetectionStrategyType getChangeDetectionStrategy() {
        return ChangeDetectionStrategyType.DEFAULT;
    }

    /**
     * Source column holding the last update timestamp for {@link ChangeDetectionStrategyType#WATERMARK}.
     */
    default String getUpdatedAtColumn() {
        return null;
    }

    /**
     * Key used in {@code dsp_sync_state} for {@link ChangeDetectionStrategyType#WATERMARK}.
     */
    default String getSyncKey() {
        return null;
    }

    /**
     * Start of the inclusive date interval used by date-range change detection.
     * {@code null} when the strategy does not need a date filter.
     */
    default LocalDate getStartDate() {
        return null;
    }

    /**
     * End of the inclusive date interval used by date-range change detection.
     * {@code null} when the strategy does not need a date filter.
     */
    default LocalDate getEndDate() {
        return null;
    }

    /** Resolves the target column name for a given source column. */
    default String resolveTargetColumn(String sourceColumn) {
        return getColumnMapping().getOrDefault(sourceColumn, sourceColumn);
    }
}
