package br.car.dsp_batch.layer.config;

import br.car.dsp_batch.layer.metadata.QualifiedTable;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for a geographic layer (source table) to migrate.
 * Each row in the source table is a feature.
 * Target table is always {@code dsp.<source-table-name>} on geo-target.
 */
@Getter
@Setter
public class LayerConfig {

    public static final String TARGET_SCHEMA = "dsp";

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

    private String sourceTable;
    private String layerName;
    private String areaOfInterestIdColumn;
    private String updatedAtColumn;
    private String whereClause = "1=1";
    private Integer srid;
    private String primaryKey;
    private String geometryColumn;
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
}
