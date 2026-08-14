package br.car.dsp_batch.layer.config;

import br.car.dsp_batch.layer.metadata.QualifiedTable;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for a geographic layer (source table) to migrate.
 * Each row in the source table is a feature.
 * Target table is always {@code dsp.<source-table-name>} on geo-target.
 *
 * <p>Mandatory source columns are mapped to fixed target names
 * ({@code id}, {@code area_of_interest_id}, {@code updated_at}, {@code label}, {@code geom}).
 * Optional extras in {@code persist-columns} keep the source column name on the target.
 */
@Getter
@Setter
public class LayerConfig {

    public static final String TARGET_SCHEMA = "dsp";

    /** Canonical primary key on every migrated layer table in geo-target. */
    public static final String ID_COLUMN = "id";

    /** Canonical FK column on every migrated layer table in geo-target. */
    public static final String AREA_OF_INTEREST_ID_COLUMN = "area_of_interest_id";

    /**
     * Canonical geometry column on every migrated layer table in geo-target.
     * Source may use another name ({@code the_geom}, {@code shape}, etc.);
     * configure {@code geometry-column} to choose which source column to migrate.
     */
    public static final String GEOMETRY_COLUMN = "geom";

    /**
     * Canonical last-update column on every migrated layer table in geo-target.
     * Source may use another name ({@code data_atualizacao}, etc.);
     * configure {@code updated-at-column} to choose which source column to migrate.
     */
    public static final String UPDATED_AT_COLUMN = "updated_at";

    /**
     * Canonical display-name column on every migrated layer table in geo-target.
     * Source may use another name ({@code nome}, {@code name}, etc.);
     * configure {@code label-column} to choose which source column to migrate.
     */
    public static final String LABEL_COLUMN = "label";

    /** Target column names reserved for the canonical contract. */
    public static final Set<String> CANONICAL_TARGET_COLUMNS = Set.of(
            ID_COLUMN,
            AREA_OF_INTEREST_ID_COLUMN,
            UPDATED_AT_COLUMN,
            LABEL_COLUMN,
            GEOMETRY_COLUMN
    );

    private String sourceTable;
    private String layerName;
    private String primaryKey;
    private String areaOfInterestIdColumn;
    private String updatedAtColumn;
    private String labelColumn;
    private String geometryColumn;
    /** Extra source columns to migrate (same name on target). */
    private List<String> persistColumns = new ArrayList<>();
    private String whereClause = "1=1";
    private Integer srid;
    /**
     * Optional IANA timezone override for interpreting TIMESTAMP/DATE watermark columns.
     * Falls back to {@code batch.source-timezone}.
     */
    private String sourceTimezone;
    private boolean enabled = true;

    public QualifiedTable resolveSourceTable() {
        return QualifiedTable.parse(sourceTable);
    }

    public QualifiedTable resolveTargetTable() {
        QualifiedTable source = resolveSourceTable();
        return new QualifiedTable(TARGET_SCHEMA, source.table());
    }

    public String resolveKey() {
        return TARGET_SCHEMA + "_" + resolveSourceTable().table();
    }

    public String resolveLayerName() {
        if (layerName != null && !layerName.isBlank()) {
            return layerName.trim();
        }
        return resolveSourceTable().table();
    }

    /**
     * Ordered set of source columns that must be migrated (required + extras).
     */
    public List<String> resolveMigratedSourceColumns() {
        Set<String> names = new LinkedHashSet<>();
        names.add(trimRequired(primaryKey));
        names.add(trimRequired(areaOfInterestIdColumn));
        names.add(trimRequired(updatedAtColumn));
        names.add(trimRequired(labelColumn));
        names.add(trimRequired(geometryColumn));
        if (persistColumns != null) {
            for (String column : persistColumns) {
                if (column != null && !column.isBlank()) {
                    names.add(column.trim());
                }
            }
        }
        return List.copyOf(names);
    }

    private static String trimRequired(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Validates YAML contract for this layer (required mappings and optional extras).
     */
    public void validate() {
        requireNonBlank("source-table", sourceTable);
        requireQualifiedTable(sourceTable);
        requireNonBlank("primary-key", primaryKey);
        requireNonBlank("area-of-interest-id-column", areaOfInterestIdColumn);
        requireNonBlank("updated-at-column", updatedAtColumn);
        requireNonBlank("label-column", labelColumn);
        requireNonBlank("geometry-column", geometryColumn);
        validatePersistColumns();
    }

    private void validatePersistColumns() {
        if (persistColumns == null || persistColumns.isEmpty()) {
            return;
        }

        Set<String> requiredSource = Set.of(
                primaryKey.trim(),
                areaOfInterestIdColumn.trim(),
                updatedAtColumn.trim(),
                labelColumn.trim(),
                geometryColumn.trim()
        );

        Set<String> seen = new LinkedHashSet<>();
        for (String raw : persistColumns) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException(
                        "persist-columns must not contain blank entries (source-table=" + sourceTable + ")");
            }
            String column = raw.trim();
            if (!seen.add(column)) {
                throw new IllegalStateException(
                        "duplicate persist-columns entry '" + column + "' (source-table=" + sourceTable + ")");
            }
            if (requiredSource.contains(column)) {
                throw new IllegalStateException(
                        "persist-columns entry '" + column
                                + "' duplicates a required column mapping (source-table=" + sourceTable + ")");
            }
            String lower = column.toLowerCase(java.util.Locale.ROOT);
            if (CANONICAL_TARGET_COLUMNS.contains(lower) || CANONICAL_TARGET_COLUMNS.contains(column)) {
                throw new IllegalStateException(
                        "persist-columns entry '" + column
                                + "' collides with a canonical target column name "
                                + CANONICAL_TARGET_COLUMNS + " (source-table=" + sourceTable + ")");
            }
        }
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("batch.layers: '" + field + "' is required");
        }
    }

    private static void requireQualifiedTable(String value) {
        try {
            QualifiedTable.parse(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "batch.layers: invalid 'source-table' value '" + value + "'. " + ex.getMessage(),
                    ex);
        }
    }
}
