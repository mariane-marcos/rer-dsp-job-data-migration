package br.car.dsp_batch.layer.config;

import br.car.dsp_batch.layer.metadata.QualifiedTable;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for a geographic layer (source table) to migrate.
 * Each row in the source table is a feature (feição).
 * Target table is always {@code dsp.<source-table-name>} on geo-target.
 */
@Getter
@Setter
public class LayerConfig {

    public static final String TARGET_SCHEMA = "dsp";

    /** Canonical FK column on every migrated layer table in geo-target. */
    public static final String AREA_OF_INTEREST_ID_COLUMN = "area_of_interest_id";

    private String sourceTable;
    private String layerName;
    private String areaOfInterestIdColumn;
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
