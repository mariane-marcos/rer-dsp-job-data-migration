package br.car.dsp_batch.aoi.config;

import br.car.dsp_batch.layer.metadata.QualifiedTable;
import br.car.dsp_batch.sync.SyncKeys;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for Area of Interest migration (source table → {@code dsp.area_of_interest}).
 *
 * <p>Mandatory source columns map to fixed target names on DSP
 * ({@code id}, {@code registration_date}, {@code updated_at}, {@code territory_level_3_id},
 * {@code area}, {@code geom}). Optional extras in {@code persist-columns} go to business +
 * geo-target. KPI columns in {@code business-only-persist-columns} are written only to dsp-db.
 */
@Getter
@Setter
public class AreaOfInterestConfig {

    public static final String ID_COLUMN = "id";
    public static final String REGISTRATION_DATE_COLUMN = "registration_date";
    public static final String UPDATED_AT_COLUMN = "updated_at";
    public static final String TERRITORY_LEVEL_3_ID_COLUMN = "territory_level_3_id";
    public static final String AREA_COLUMN = "area";
    public static final String GEOMETRY_COLUMN = "geom";

    public static final Set<String> CANONICAL_TARGET_COLUMNS = Set.of(
            ID_COLUMN,
            REGISTRATION_DATE_COLUMN,
            UPDATED_AT_COLUMN,
            TERRITORY_LEVEL_3_ID_COLUMN,
            AREA_COLUMN,
            GEOMETRY_COLUMN
    );

    private String sourceTable;
    private String targetTable = "dsp.area_of_interest";
    private String primaryKey;
    private String creationDateColumn;
    private String updatedAtColumn;
    private String communeIdColumn;
    private String totalAreaColumn;
    private String geometryColumn;
    /** Extra source columns migrated to business + geo-target (same name on target). */
    private List<String> persistColumns = new ArrayList<>();
    /** KPI columns migrated only to dsp-db (same name on target), e.g. theme_1…theme_4. */
    private List<String> businessOnlyPersistColumns = new ArrayList<>();
    private String whereClause = "1=1";
    private Integer srid;
    private String layerName;
    private String sourceTimezone;
    private String syncKey;

    public QualifiedTable resolveSourceTable() {
        return QualifiedTable.parse(sourceTable);
    }

    public QualifiedTable resolveTargetTable() {
        return QualifiedTable.parse(targetTable);
    }

    public String resolveSyncKey() {
        if (syncKey != null && !syncKey.isBlank()) {
            return syncKey.trim();
        }
        return SyncKeys.AREA_OF_INTEREST;
    }

    public String resolveLayerName() {
        if (layerName != null && !layerName.isBlank()) {
            return layerName.trim();
        }
        return resolveTargetTable().table();
    }

    public void validate() {
        requireNonBlank("source-table", sourceTable);
        requireQualifiedTable(sourceTable);
        requireNonBlank("target-table", targetTable);
        requireQualifiedTable(targetTable);
        requireNonBlank("primary-key", primaryKey);
        requireNonBlank("creation-date-column", creationDateColumn);
        requireNonBlank("updated-at-column", updatedAtColumn);
        requireNonBlank("commune-id-column", communeIdColumn);
        requireNonBlank("total-area-column", totalAreaColumn);
        requireNonBlank("geometry-column", geometryColumn);
        if (srid == null || srid <= 0) {
            throw new IllegalStateException(
                    "batch.area-of-interest: 'srid' must be a positive integer");
        }
        validateOptionalColumnList(
                persistColumns,
                "persist-columns",
                Set.of(),
                true);
        validateOptionalColumnList(
                businessOnlyPersistColumns,
                "business-only-persist-columns",
                normalizedOptionalColumns(persistColumns),
                false);
    }

    private void validateOptionalColumnList(List<String> columns,
                                            String fieldName,
                                            Set<String> forbiddenDuplicates,
                                            boolean validateAgainstBusinessOnly) {
        if (columns == null || columns.isEmpty()) {
            return;
        }

        Set<String> requiredSource = Set.of(
                primaryKey.trim(),
                creationDateColumn.trim(),
                updatedAtColumn.trim(),
                communeIdColumn.trim(),
                totalAreaColumn.trim(),
                geometryColumn.trim()
        );

        Set<String> forbidden = new LinkedHashSet<>(forbiddenDuplicates);
        if (validateAgainstBusinessOnly) {
            forbidden.addAll(normalizedOptionalColumns(businessOnlyPersistColumns));
        }

        Set<String> seen = new LinkedHashSet<>();
        for (String raw : columns) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException(
                        fieldName + " must not contain blank entries (source-table=" + sourceTable + ")");
            }
            String column = raw.trim();
            if (!seen.add(column)) {
                throw new IllegalStateException(
                        "duplicate " + fieldName + " entry '" + column + "' (source-table=" + sourceTable + ")");
            }
            if (requiredSource.contains(column)) {
                throw new IllegalStateException(
                        fieldName + " entry '" + column
                                + "' duplicates a required column mapping (source-table=" + sourceTable + ")");
            }
            if (forbidden.contains(column)) {
                throw new IllegalStateException(
                        fieldName + " entry '" + column
                                + "' duplicates an entry in another optional column list "
                                + "(source-table=" + sourceTable + ")");
            }
            if (CANONICAL_TARGET_COLUMNS.contains(column)) {
                throw new IllegalStateException(
                        fieldName + " entry '" + column
                                + "' collides with a canonical target column name "
                                + CANONICAL_TARGET_COLUMNS + " (source-table=" + sourceTable + ")");
            }
        }
    }

    private static Set<String> normalizedOptionalColumns(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String column : columns) {
            if (column != null && !column.isBlank()) {
                normalized.add(column.trim());
            }
        }
        return normalized;
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("batch.area-of-interest: '" + field + "' is required");
        }
    }

    private static void requireQualifiedTable(String value) {
        try {
            QualifiedTable.parse(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "batch.area-of-interest: invalid table value '" + value + "'. " + ex.getMessage(),
                    ex);
        }
    }
}
