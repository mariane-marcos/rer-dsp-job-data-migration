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
        GeometryInfo geometryInfo = resolveGeometry(sourceJdbc, source, columns, config.getGeometryColumn());
        warnIfHigherDimensional(source, geometryInfo);
        int srid = resolveSrid(sourceJdbc, source, geometryInfo, config.getSrid());
        List<IndexMetadata> indexes = fetchIndexes(sourceJdbc, source);

        log.info(
                "Introspection completed for {}: pk={}, aoiLink={}, geom={}, srid={}, columns={}",
                source.qualified(),
                primaryKey,
                areaOfInterestIdColumn,
                geometryInfo.columnName(),
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
            requireColumn(columns, override.trim(), "geometry-column");
            return new GeometryInfo(override.trim(), null, null);
        }

        List<GeometryInfo> geometries = jdbc.query(
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

        if (geometries.isEmpty()) {
            List<String> geometryColumns = columns.stream()
                    .filter(ColumnMetadata::geometry)
                    .map(ColumnMetadata::name)
                    .toList();
            if (geometryColumns.isEmpty()) {
                throw new IllegalStateException(
                        "Table " + table.qualified()
                                + " has no geometry column. Set geometry-column.");
            }
            if (geometryColumns.size() > 1) {
                log.warn(
                        "Table {} has multiple geometry columns ({}). Using '{}'.",
                        table.qualified(),
                        geometryColumns,
                        geometryColumns.getFirst()
                );
            }
            return new GeometryInfo(geometryColumns.getFirst(), null, null);
        }

        if (geometries.size() > 1) {
            log.warn(
                    "Table {} has multiple geometry_columns entries ({}). Using '{}'.",
                    table.qualified(),
                    geometries.stream().map(GeometryInfo::columnName).toList(),
                    geometries.getFirst().columnName()
            );
        }
        return geometries.getFirst();
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
                            + ". Set batch.layers.srid.");
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
