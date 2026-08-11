package br.car.dsp_batch.layer.introspection;

import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.IndexMetadata;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static br.car.dsp_batch.layer.config.LayerConfig.GEOMETRY_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.UPDATED_AT_COLUMN;

/**
 * Automatically discovers PostGIS table structure on the source database.
 */
@Slf4j
@Service
public class SchemaIntrospectionService {

    public boolean tableExists(JdbcTemplate jdbc, QualifiedTable table) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ? AND table_name = ?
                """,
                Integer.class,
                table.schema(),
                table.table()
        );
        return count != null && count > 0;
    }

    public LayerTableMetadata introspect(JdbcTemplate sourceJdbc, LayerConfig config) {
        QualifiedTable source = config.resolveSourceTable();
        QualifiedTable target = config.resolveTargetTable();

        if (!tableExists(sourceJdbc, source)) {
            throw new IllegalStateException(
                    "Source table not found: " + source.qualified());
        }

        List<ColumnMetadata> columns = fetchColumns(sourceJdbc, source);
        if (columns.isEmpty()) {
            throw new IllegalStateException(
                    "Source table has no columns: " + source.qualified());
        }

        String primaryKey = resolvePrimaryKey(sourceJdbc, source, columns, config.getPrimaryKey());
        String areaOfInterestIdColumn = config.getAreaOfInterestIdColumn().trim();
        requireColumn(columns, areaOfInterestIdColumn, "area-of-interest-id-column");
        String updatedAtColumn = requireUpdatedAtColumn(columns, config.getUpdatedAtColumn());
        GeometryInfo geometryInfo = resolveGeometry(sourceJdbc, source, columns, config.getGeometryColumn());
        rejectGeomNameCollision(source, columns, geometryInfo.columnName());
        rejectUpdatedAtNameCollision(source, columns, updatedAtColumn);
        warnIfHigherDimensional(source, geometryInfo);
        int srid = resolveSrid(sourceJdbc, source, geometryInfo, config.getSrid());
        List<IndexMetadata> indexes = fetchIndexes(sourceJdbc, source);

        log.info(
                "Introspection completed for {}: pk={}, aoiLink={}, sourceUpdatedAt={} -> targetUpdatedAt={}, "
                        + "sourceGeom={} -> targetGeom={}, srid={}, columns={}",
                source.qualified(),
                primaryKey,
                areaOfInterestIdColumn,
                updatedAtColumn,
                UPDATED_AT_COLUMN,
                geometryInfo.columnName(),
                GEOMETRY_COLUMN,
                srid,
                columns.size()
        );

        return new LayerTableMetadata(
                config.resolveKey(),
                config.resolveLayerName(),
                source,
                target,
                primaryKey,
                geometryInfo.columnName(),
                areaOfInterestIdColumn,
                updatedAtColumn,
                srid,
                columns,
                indexes,
                config.getWhereClause()
        );
    }

    public LayerTableMetadata introspect(DataSource sourceDataSource, LayerConfig config) {
        return introspect(new JdbcTemplate(sourceDataSource), config);
    }

    private List<ColumnMetadata> fetchColumns(JdbcTemplate jdbc, QualifiedTable table) {
        return jdbc.query(
                """
                SELECT column_name,
                       udt_name,
                       character_maximum_length,
                       numeric_precision,
                       numeric_scale,
                       is_nullable
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """,
                (rs, rowNum) -> {
                    String name = rs.getString("column_name");
                    String udtName = rs.getString("udt_name");
                    boolean geometry = "geometry".equalsIgnoreCase(udtName)
                            || "geography".equalsIgnoreCase(udtName);
                    return new ColumnMetadata(
                            name,
                            udtName,
                            (Integer) rs.getObject("character_maximum_length"),
                            (Integer) rs.getObject("numeric_precision"),
                            (Integer) rs.getObject("numeric_scale"),
                            "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                            geometry
                    );
                },
                table.schema(),
                table.table()
        );
    }

    private String resolvePrimaryKey(JdbcTemplate jdbc,
                                     QualifiedTable table,
                                     List<ColumnMetadata> columns,
                                     String override) {
        if (override != null && !override.isBlank()) {
            requireColumn(columns, override.trim(), "primary-key");
            return override.trim();
        }

        List<String> pkColumns = jdbc.query(
                """
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                 AND tc.table_name = kcu.table_name
                WHERE tc.constraint_type = 'PRIMARY KEY'
                  AND tc.table_schema = ?
                  AND tc.table_name = ?
                ORDER BY kcu.ordinal_position
                """,
                (rs, rowNum) -> rs.getString("column_name"),
                table.schema(),
                table.table()
        );

        if (pkColumns.isEmpty()) {
            throw new IllegalStateException(
                    "Table " + table.qualified()
                            + " has no PRIMARY KEY. Set batch.layers.primary-key.");
        }
        if (pkColumns.size() > 1) {
            throw new IllegalStateException(
                    "Table " + table.qualified()
                            + " has a composite primary key (" + String.join(", ", pkColumns)
                            + "). Not supported in v1.");
        }
        return pkColumns.getFirst();
    }

    private GeometryInfo resolveGeometry(JdbcTemplate jdbc,
                                         QualifiedTable table,
                                         List<ColumnMetadata> columns,
                                         String override) {
        if (override != null && !override.isBlank()) {
            String chosen = override.trim();
            requireColumn(columns, chosen, "geometry-column");
            requireGeometryTypedColumn(columns, chosen);
            GeometryInfo registered = findRegisteredGeometry(jdbc, table, chosen);
            if (registered != null) {
                return registered;
            }
            return new GeometryInfo(chosen, null, null);
        }

        List<GeometryInfo> geometries = fetchRegisteredGeometries(jdbc, table);

        if (geometries.isEmpty()) {
            List<String> geometryColumns = columns.stream()
                    .filter(ColumnMetadata::geometry)
                    .map(ColumnMetadata::name)
                    .toList();
            if (geometryColumns.isEmpty()) {
                throw new IllegalStateException(
                        "Table " + table.qualified()
                                + " has no geometry column. Set batch.layers.geometry-column "
                                + "to the source column that should become '" + GEOMETRY_COLUMN + "'.");
            }
            if (geometryColumns.size() > 1) {
                log.warn(
                        "Table {} has multiple geometry columns ({}). Using '{}'. "
                                + "Set batch.layers.geometry-column to choose another.",
                        table.qualified(),
                        geometryColumns,
                        geometryColumns.getFirst()
                );
            }
            return new GeometryInfo(geometryColumns.getFirst(), null, null);
        }

        if (geometries.size() > 1) {
            log.warn(
                    "Table {} has multiple geometry_columns entries ({}). Using '{}'. "
                            + "Set batch.layers.geometry-column to choose another.",
                    table.qualified(),
                    geometries.stream().map(GeometryInfo::columnName).toList(),
                    geometries.getFirst().columnName()
            );
        }
        return geometries.getFirst();
    }

    private List<GeometryInfo> fetchRegisteredGeometries(JdbcTemplate jdbc, QualifiedTable table) {
        return jdbc.query(
                """
                SELECT f_geometry_column, srid, type
                FROM geometry_columns
                WHERE f_table_schema = ? AND f_table_name = ?
                ORDER BY f_geometry_column
                """,
                (rs, rowNum) -> new GeometryInfo(
                        rs.getString("f_geometry_column"),
                        (Integer) rs.getObject("srid"),
                        rs.getString("type")
                ),
                table.schema(),
                table.table()
        );
    }

    private GeometryInfo findRegisteredGeometry(JdbcTemplate jdbc,
                                                QualifiedTable table,
                                                String columnName) {
        List<GeometryInfo> matched = jdbc.query(
                """
                SELECT f_geometry_column, srid, type
                FROM geometry_columns
                WHERE f_table_schema = ? AND f_table_name = ? AND f_geometry_column = ?
                """,
                (rs, rowNum) -> new GeometryInfo(
                        rs.getString("f_geometry_column"),
                        (Integer) rs.getObject("srid"),
                        rs.getString("type")
                ),
                table.schema(),
                table.table(),
                columnName
        );
        return matched.isEmpty() ? null : matched.getFirst();
    }

    private void requireGeometryTypedColumn(List<ColumnMetadata> columns, String name) {
        boolean geometryTyped = columns.stream()
                .anyMatch(column -> column.name().equals(name) && column.geometry());
        if (!geometryTyped) {
            throw new IllegalStateException(
                    "geometry-column '" + name + "' exists but is not geometry/geography. "
                            + "Set batch.layers.geometry-column to a PostGIS geometry column "
                            + "(it will be stored as '" + GEOMETRY_COLUMN + "' on geo-target).");
        }
    }

    /**
     * Target always uses {@code geom}; reject a non-geometry source attribute with that name
     * when the migrated geometry column itself has another name.
     */
    private void rejectGeomNameCollision(QualifiedTable table,
                                         List<ColumnMetadata> columns,
                                         String geometryColumnName) {
        if (GEOMETRY_COLUMN.equals(geometryColumnName)) {
            return;
        }
        boolean conflict = columns.stream()
                .anyMatch(column -> !column.geometry() && GEOMETRY_COLUMN.equals(column.name()));
        if (conflict) {
            throw new IllegalStateException(
                    "Table " + table.qualified()
                            + " has a non-geometry column named '" + GEOMETRY_COLUMN
                            + "' while the geometry to migrate is '" + geometryColumnName
                            + "'. Rename that attribute on the source, or set geometry-column: "
                            + GEOMETRY_COLUMN + " if that is the column to migrate "
                            + "(destination geometry is always '" + GEOMETRY_COLUMN + "').");
        }
    }

    private static final Set<String> UPDATED_AT_UDT_NAMES = Set.of(
            "timestamp",
            "timestamptz",
            "date"
    );

    private String requireUpdatedAtColumn(List<ColumnMetadata> columns, String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "updated-at-column is required. Set batch.layers.updated-at-column "
                            + "to the source column that should become '" + UPDATED_AT_COLUMN + "'.");
        }
        String name = configured.trim();
        requireColumn(columns, name, "updated-at-column");
        ColumnMetadata column = columns.stream()
                .filter(col -> col.name().equals(name))
                .findFirst()
                .orElseThrow();
        String udt = column.udtName() == null ? "" : column.udtName().toLowerCase(Locale.ROOT);
        if (!UPDATED_AT_UDT_NAMES.contains(udt)) {
            throw new IllegalStateException(
                    "updated-at-column '" + name + "' has type '" + column.udtName()
                            + "'. Expected timestamp, timestamptz or date "
                            + "(it will be stored as '" + UPDATED_AT_COLUMN + "' on geo-target).");
        }
        return name;
    }

    /**
     * Target always uses {@code updated_at}; reject another source attribute with that name
     * when the configured update column itself has another name.
     */
    private void rejectUpdatedAtNameCollision(QualifiedTable table,
                                              List<ColumnMetadata> columns,
                                              String updatedAtColumnName) {
        if (UPDATED_AT_COLUMN.equals(updatedAtColumnName)) {
            return;
        }
        boolean conflict = columns.stream()
                .anyMatch(column -> UPDATED_AT_COLUMN.equals(column.name()));
        if (conflict) {
            throw new IllegalStateException(
                    "Table " + table.qualified()
                            + " has a column named '" + UPDATED_AT_COLUMN
                            + "' while the update column to migrate is '" + updatedAtColumnName
                            + "'. Rename that attribute on the source, or set updated-at-column: "
                            + UPDATED_AT_COLUMN + " if that is the column to migrate "
                            + "(destination update column is always '" + UPDATED_AT_COLUMN + "').");
        }
    }

    private void warnIfHigherDimensional(QualifiedTable table, GeometryInfo geometryInfo) {
        String type = geometryInfo.geometryType();
        if (type == null || type.isBlank()) {
            return;
        }
        String upper = type.toUpperCase();
        if (upper.contains("Z") || upper.contains("M")) {
            log.warn(
                    "Table {} geometry column '{}' is {} — Z/M will be dropped (ST_Force2D) on geo-target.",
                    table.qualified(),
                    geometryInfo.columnName(),
                    type
            );
        }
    }

    private int resolveSrid(JdbcTemplate jdbc,
                            QualifiedTable table,
                            GeometryInfo geometryInfo,
                            Integer override) {
        if (override != null && override > 0) {
            return override;
        }
        if (geometryInfo.registeredSrid() != null && geometryInfo.registeredSrid() > 0) {
            return geometryInfo.registeredSrid();
        }

        Integer sampledSrid = jdbc.query(
                """
                SELECT DISTINCT ST_SRID(%s) AS srid
                FROM %s
                WHERE %s IS NOT NULL
                LIMIT 1
                """.formatted(
                        quoteIdentifier(geometryInfo.columnName()),
                        table.qualified(),
                        quoteIdentifier(geometryInfo.columnName())
                ),
                rs -> rs.next() ? rs.getInt("srid") : null
        );

        if (sampledSrid == null || sampledSrid <= 0) {
            throw new IllegalStateException(
                    "SRID not found for " + table.qualified()
                            + ". Set batch.layers.srid to the SRID that should be used "
                            + "for this layer on geo-target.");
        }
        return sampledSrid;
    }

    private List<IndexMetadata> fetchIndexes(JdbcTemplate jdbc, QualifiedTable table) {
        List<IndexRow> rows = jdbc.query(
                """
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = ? AND tablename = ?
                ORDER BY indexname
                """,
                (rs, rowNum) -> new IndexRow(
                        rs.getString("indexname"),
                        rs.getString("indexdef")
                ),
                table.schema(),
                table.table()
        );

        List<IndexMetadata> indexes = new ArrayList<>();
        for (IndexRow row : rows) {
            if (row.indexName().endsWith("_pkey")) {
                continue;
            }
            indexes.add(parseIndex(row));
        }
        return indexes;
    }

    private IndexMetadata parseIndex(IndexRow row) {
        String definition = row.indexDef();
        boolean unique = definition.toUpperCase().contains("UNIQUE INDEX");
        String method = "btree";
        if (definition.toUpperCase().contains("USING GIST")) {
            method = "gist";
        } else if (definition.toUpperCase().contains("USING GIN")) {
            method = "gin";
        }

        int openParen = definition.indexOf('(');
        int closeParen = definition.lastIndexOf(')');
        List<String> columns = List.of();
        if (openParen >= 0 && closeParen > openParen) {
            String columnsPart = definition.substring(openParen + 1, closeParen);
            columns = List.of(columnsPart.split(",")).stream()
                    .map(String::trim)
                    .map(this::stripQuotes)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        return new IndexMetadata(row.indexName(), columns, method, unique);
    }

    private void requireColumn(List<ColumnMetadata> columns, String name, String field) {
        boolean exists = columns.stream().anyMatch(col -> col.name().equals(name));
        if (!exists) {
            throw new IllegalStateException(
                    field + " '" + name + "' does not exist on the source table.");
        }
    }

    private String stripQuotes(String value) {
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record GeometryInfo(String columnName, Integer registeredSrid, String geometryType) {
    }

    private record IndexRow(String indexName, String indexDef) {
    }
}
