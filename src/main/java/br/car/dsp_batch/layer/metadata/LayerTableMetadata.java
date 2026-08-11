package br.car.dsp_batch.layer.metadata;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static br.car.dsp_batch.layer.config.LayerConfig.AREA_OF_INTEREST_ID_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.GEOMETRY_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.UPDATED_AT_COLUMN;

/**
 * Metadata for a layer table discovered on the source database.
 * Rows in this table represent features.
 */
public record LayerTableMetadata(
        String layerKey,
        String layerName,
        QualifiedTable sourceTable,
        QualifiedTable targetTable,
        String primaryKeyColumn,
        String geometryColumn,
        String areaOfInterestIdSourceColumn,
        String updatedAtSourceColumn,
        int srid,
        List<ColumnMetadata> columns,
        List<IndexMetadata> indexes,
        String whereClause
) {

    public List<String> sourceNonGeometryColumnNames() {
        List<String> names = new ArrayList<>();
        for (ColumnMetadata column : columns) {
            if (!column.geometry()) {
                names.add(column.name());
            }
        }
        return names;
    }

    public List<String> targetNonGeometryColumnNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String sourceColumn : sourceNonGeometryColumnNames()) {
            names.add(resolveTargetColumnName(sourceColumn));
        }
        return List.copyOf(names);
    }

    public List<String> sourceComparisonColumnNames() {
        return sourceNonGeometryColumnNames();
    }

    public List<String> targetComparisonColumnNames() {
        return targetNonGeometryColumnNames();
    }

    /**
     * Geometry column name on geo-target (always {@code geom}).
     */
    public String resolveTargetGeometryColumn() {
        return GEOMETRY_COLUMN;
    }

    /**
     * Last-update column name on geo-target (always {@code updated_at}).
     */
    public String resolveTargetUpdatedAtColumn() {
        return UPDATED_AT_COLUMN;
    }

    public String resolveTargetColumnName(String sourceColumnName) {
        if (sourceColumnName.equals(geometryColumn)) {
            return GEOMETRY_COLUMN;
        }
        if (sourceColumnName.equals(updatedAtSourceColumn)
                && !UPDATED_AT_COLUMN.equals(sourceColumnName)) {
            return UPDATED_AT_COLUMN;
        }
        if (sourceColumnName.equals(areaOfInterestIdSourceColumn)
                && !AREA_OF_INTEREST_ID_COLUMN.equals(sourceColumnName)) {
            return AREA_OF_INTEREST_ID_COLUMN;
        }
        return sourceColumnName;
    }

    public String resolveSourceColumnName(String targetColumnName) {
        if (GEOMETRY_COLUMN.equals(targetColumnName)
                && !GEOMETRY_COLUMN.equals(geometryColumn)) {
            return geometryColumn;
        }
        if (UPDATED_AT_COLUMN.equals(targetColumnName)
                && !UPDATED_AT_COLUMN.equals(updatedAtSourceColumn)) {
            return updatedAtSourceColumn;
        }
        if (AREA_OF_INTEREST_ID_COLUMN.equals(targetColumnName)
                && !AREA_OF_INTEREST_ID_COLUMN.equals(areaOfInterestIdSourceColumn)) {
            return areaOfInterestIdSourceColumn;
        }
        return targetColumnName;
    }

    public String resolveTargetPrimaryKeyColumn() {
        return resolveTargetColumnName(primaryKeyColumn);
    }

    public String qualifiedSourceTable() {
        return sourceTable.qualified();
    }

    public String qualifiedTargetTable() {
        return targetTable.qualified();
    }
}
