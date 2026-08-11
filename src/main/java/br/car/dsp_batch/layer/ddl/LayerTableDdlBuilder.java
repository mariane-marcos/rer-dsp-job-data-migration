package br.car.dsp_batch.layer.ddl;

import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.IndexMetadata;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds CREATE TABLE and index DDL statements for the target database.
 */
@Slf4j
@Component
public class LayerTableDdlBuilder {

    private final PostgresTypeMapper typeMapper;

    public LayerTableDdlBuilder(PostgresTypeMapper typeMapper) {
        this.typeMapper = typeMapper;
    }

    public List<String> buildStatements(LayerTableMetadata metadata) {
        List<String> statements = new ArrayList<>();
        statements.add(buildCreateTable(metadata));
        // Existing tables (CREATE IF NOT EXISTS) may not have updated_at yet.
        statements.add(buildEnsureUpdatedAtColumn(metadata));
        statements.add(buildGeometryIndex(metadata));
        statements.add(buildAreaOfInterestIdIndex(metadata));
        statements.add(buildUpdatedAtIndex(metadata));
        statements.addAll(buildSecondaryIndexes(metadata));
        return statements;
    }

    /**
     * Ensures the canonical {@code updated_at} column exists on tables created before this rule.
     */
    public String buildEnsureUpdatedAtColumn(LayerTableMetadata metadata) {
        return "ALTER TABLE " + metadata.qualifiedTargetTable()
                + " ADD COLUMN IF NOT EXISTS "
                + quote(LayerConfig.UPDATED_AT_COLUMN)
                + " timestamptz";
    }

    public String buildUpdatedAtIndex(LayerTableMetadata metadata) {
        String indexName = sanitizeIndexName(metadata.targetTable().table() + "_updated_at");
        return "CREATE INDEX IF NOT EXISTS " + quote(indexName)
                + " ON " + metadata.qualifiedTargetTable()
                + " (" + quote(LayerConfig.UPDATED_AT_COLUMN) + ")";
    }

    public String buildCreateTable(LayerTableMetadata metadata) {
        List<String> columnDefinitions = new ArrayList<>();
        Set<String> emittedTargetColumns = new LinkedHashSet<>();

        for (ColumnMetadata column : metadata.columns()) {
            if (column.geometry()) {
                // Only the chosen source geometry is migrated; always as canonical "geom".
                if (!column.name().equals(metadata.geometryColumn())) {
                    continue;
                }
                String targetGeom = metadata.resolveTargetGeometryColumn();
                if (!emittedTargetColumns.add(targetGeom)) {
                    continue;
                }
                columnDefinitions.add(quote(targetGeom) + " geometry(Geometry, "
                        + metadata.srid() + ")");
                continue;
            }

            String targetColumnName = metadata.resolveTargetColumnName(column.name());
            if (!emittedTargetColumns.add(targetColumnName)) {
                continue;
            }

            String ddlType = typeMapper.toDdlType(column);
            columnDefinitions.add(quote(targetColumnName) + " " + ddlType);
        }

        String targetPk = metadata.resolveTargetPrimaryKeyColumn();
        columnDefinitions.add("PRIMARY KEY (" + quote(targetPk) + ")");

        return "CREATE TABLE IF NOT EXISTS " + metadata.qualifiedTargetTable()
                + " (\n    "
                + String.join(",\n    ", columnDefinitions)
                + "\n)";
    }

    public String buildGeometryIndex(LayerTableMetadata metadata) {
        String indexName = sanitizeIndexName(metadata.targetTable().table() + "_geom_gist");
        return "CREATE INDEX IF NOT EXISTS " + quote(indexName)
                + " ON " + metadata.qualifiedTargetTable()
                + " USING GIST (" + quote(metadata.resolveTargetGeometryColumn()) + ")";
    }

    public String buildAreaOfInterestIdIndex(LayerTableMetadata metadata) {
        String indexName = sanitizeIndexName(metadata.targetTable().table() + "_aoi_id");
        return "CREATE INDEX IF NOT EXISTS " + quote(indexName)
                + " ON " + metadata.qualifiedTargetTable()
                + " (" + quote(LayerConfig.AREA_OF_INTEREST_ID_COLUMN) + ")";
    }

    public List<String> buildSecondaryIndexes(LayerTableMetadata metadata) {
        List<String> statements = new ArrayList<>();
        Set<String> targetColumns = new LinkedHashSet<>(metadata.targetNonGeometryColumnNames());
        targetColumns.add(metadata.resolveTargetGeometryColumn());

        for (IndexMetadata index : metadata.indexes()) {
            if (index.columns().isEmpty()) {
                continue;
            }

            // Indexes on non-migrated geometry columns are skipped (column absent on target).
            List<String> mappedColumns = index.columns().stream()
                    .map(metadata::resolveTargetColumnName)
                    .toList();

            List<String> missingColumns = mappedColumns.stream()
                    .filter(column -> !targetColumns.contains(column))
                    .toList();
            if (!missingColumns.isEmpty()) {
                log.warn(
                        "Skipping secondary index '{}' on {}: mapped column(s) {} not present on target "
                                + "(source columns: {})",
                        index.name(),
                        metadata.qualifiedTargetTable(),
                        missingColumns,
                        index.columns()
                );
                continue;
            }

            if (isRedundantWithCanonicalIndexes(metadata, mappedColumns, index.method())) {
                continue;
            }

            String indexName = sanitizeIndexName(metadata.targetTable().table() + "_" + index.name());
            String columns = String.join(", ", mappedColumns.stream().map(this::quote).toList());
            String unique = index.unique() ? "UNIQUE " : "";
            String method = index.method() != null && !index.method().isBlank()
                    ? " USING " + index.method().toUpperCase()
                    : "";

            statements.add("CREATE " + unique + "INDEX IF NOT EXISTS " + quote(indexName)
                    + " ON " + metadata.qualifiedTargetTable()
                    + method + " (" + columns + ")");
        }
        return statements;
    }

    /**
     * Indexes already created by canonical index builders (geom / AOI / updated_at).
     */
    private boolean isRedundantWithCanonicalIndexes(LayerTableMetadata metadata,
                                                    List<String> mappedColumns,
                                                    String method) {
        if (mappedColumns.size() != 1) {
            return false;
        }
        String only = mappedColumns.getFirst();
        if (only.equals(metadata.resolveTargetGeometryColumn()) && "gist".equalsIgnoreCase(method)) {
            return true;
        }
        if (only.equals(LayerConfig.AREA_OF_INTEREST_ID_COLUMN)) {
            return true;
        }
        return only.equals(LayerConfig.UPDATED_AT_COLUMN);
    }

    private String sanitizeIndexName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
