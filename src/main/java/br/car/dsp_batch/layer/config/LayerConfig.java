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
}
