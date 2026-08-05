package br.car.dsp_batch.layer.metadata;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static br.car.dsp_batch.layer.config.LayerConfig.AREA_OF_INTEREST_ID_COLUMN;

/**
 * Metadata for a layer table discovered on the source database.
 * Rows in this table represent features (feições).
 */
public record LayerTableMetadata(
        String layerKey,
        String layerName,
        QualifiedTable sourceTable,
        QualifiedTable targetTable,
        String primaryKeyColumn,
        String geometryColumn,
        String areaOfInterestIdSourceColumn,
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

    public String resolveTargetColumnName(String sourceColumnName) {
        if (sourceColumnName.equals(areaOfInterestIdSourceColumn)
                && !AREA_OF_INTEREST_ID_COLUMN.equals(sourceColumnName)) {
            return AREA_OF_INTEREST_ID_COLUMN;
        }
        return sourceColumnName;
    }

    public String resolveSourceColumnName(String targetColumnName) {
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
