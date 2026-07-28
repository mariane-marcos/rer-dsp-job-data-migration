package br.car.dsp_batch.service;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dual-write persistence: business target (bbox/centroid) + geo target (full geometry).
 * Fail-fast: any destination failure aborts the chunk; re-run is idempotent via UPSERT.
 */
@Slf4j
@Service
public class AdministrativeUnitPersistenceService {

    private static final String BOUNDARY_BOX_COLUMN = "boundary_box";
    private static final String CENTROID_COLUMN = "centroid_coordinates";

    private final JdbcTemplate targetJdbcTemplate;
    private final JdbcTemplate geoTargetJdbcTemplate;

    public AdministrativeUnitPersistenceService(
            @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate,
            @Qualifier("geoTargetJdbcTemplate") JdbcTemplate geoTargetJdbcTemplate) {
        this.targetJdbcTemplate = targetJdbcTemplate;
        this.geoTargetJdbcTemplate = geoTargetJdbcTemplate;
    }

    public void upsertAll(List<AdministrativeUnitDTO> items, JobTableConfig tableConfig) {
        if (items == null || items.isEmpty()) {
            return;
        }

        requirePositiveSrid(tableConfig);

        List<AdministrativeUnitDTO> validItems = new ArrayList<>();
        int skippedWithoutGeometry = 0;
        for (AdministrativeUnitDTO item : items) {
            if (item.getGeometryGeoJson() != null && !item.getGeometryGeoJson().isBlank()) {
                validItems.add(item);
            } else {
                skippedWithoutGeometry++;
                log.warn("Skipping record id={} due to null/empty geometry", item.getId());
            }
        }

        if (validItems.isEmpty()) {
            log.info(
                    "Upserted 0 records into {} / geo-target (skipped {} without geometry)",
                    tableConfig.getTargetTable(),
                    skippedWithoutGeometry
            );
            return;
        }

        upsertBusinessTarget(validItems, tableConfig);
        upsertGeoTarget(validItems, tableConfig);

        log.info(
                "Upserted {} records into business target {} and geo-target (skipped {} without geometry)",
                validItems.size(),
                tableConfig.getTargetTable(),
                skippedWithoutGeometry
        );
    }

    private void upsertBusinessTarget(List<AdministrativeUnitDTO> items, JobTableConfig tableConfig) {
        List<String> sourceColumns = tableConfig.getPersistColumns();
        List<String> targetColumns = sourceColumns.stream()
                .map(tableConfig::resolveTargetColumn)
                .collect(Collectors.toList());
        String targetPk = tableConfig.resolveTargetColumn(tableConfig.getPrimaryKey());
        int srid = tableConfig.getSrid();

        String insertColumns = String.join(", ", targetColumns)
                + ", " + BOUNDARY_BOX_COLUMN
                + ", " + CENTROID_COLUMN;
        String placeholders = sourceColumns.stream()
                .map(c -> "?")
                .collect(Collectors.joining(", "));
        String geomExpr = String.format(
                "public.ST_SetSRID(public.ST_GeomFromGeoJSON(?), %d)",
                srid
        );
        String boundaryPlaceholder = "public.ST_Envelope(" + geomExpr + ")";
        String centroidPlaceholder = "public.ST_Centroid(" + geomExpr + ")";

        String updateSet = targetColumns.stream()
                .filter(col -> !col.equals(targetPk))
                .map(col -> col + " = EXCLUDED." + col)
                .collect(Collectors.joining(", "));
        String geoUpdate = BOUNDARY_BOX_COLUMN + " = EXCLUDED." + BOUNDARY_BOX_COLUMN
                + ", " + CENTROID_COLUMN + " = EXCLUDED." + CENTROID_COLUMN;
        updateSet = updateSet.isEmpty() ? geoUpdate : updateSet + ", " + geoUpdate;

        String sql = String.format(
                "INSERT INTO %s (%s) VALUES (%s, %s, %s) ON CONFLICT (%s) DO UPDATE SET %s",
                tableConfig.getTargetTable(),
                insertColumns,
                placeholders,
                boundaryPlaceholder,
                centroidPlaceholder,
                targetPk,
                updateSet
        );

        try {
            targetJdbcTemplate.batchUpdate(sql, items, items.size(), (ps, item) -> {
                int index = 1;
                for (String column : sourceColumns) {
                    ps.setObject(index++, item.getAttribute(column));
                }
                ps.setString(index++, item.getGeometryGeoJson());
                ps.setString(index, item.getGeometryGeoJson());
            });
        } catch (DataAccessException e) {
            log.error("Critical failure during business-target UPSERT for {}",
                    tableConfig.getTargetTable(), e);
            throw new RuntimeException(
                    "Error executing business-target UPSERT for "
                            + tableConfig.getTargetTable() + ": " + e.getMessage(),
                    e);
        }
    }

    private void upsertGeoTarget(List<AdministrativeUnitDTO> items, JobTableConfig tableConfig) {
        List<String> sourceColumns = tableConfig.getPersistColumns();
        List<String> targetColumns = sourceColumns.stream()
                .map(tableConfig::resolveTargetColumn)
                .collect(Collectors.toList());
        String targetGeom = tableConfig.resolveTargetColumn(tableConfig.getGeometryColumn());
        String targetPk = tableConfig.resolveTargetColumn(tableConfig.getPrimaryKey());
        int srid = tableConfig.getSrid();

        String insertColumns = String.join(", ", targetColumns) + ", " + targetGeom;
        String placeholders = sourceColumns.stream()
                .map(c -> "?")
                .collect(Collectors.joining(", "));
        String geometryPlaceholder = String.format(
                "public.ST_SetSRID(public.ST_GeomFromGeoJSON(?), %d)",
                srid
        );

        String updateSet = targetColumns.stream()
                .filter(col -> !col.equals(targetPk))
                .map(col -> col + " = EXCLUDED." + col)
                .collect(Collectors.joining(", "));
        updateSet = updateSet.isEmpty()
                ? targetGeom + " = EXCLUDED." + targetGeom
                : updateSet + ", " + targetGeom + " = EXCLUDED." + targetGeom;

        String sql = String.format(
                "INSERT INTO %s (%s) VALUES (%s, %s) ON CONFLICT (%s) DO UPDATE SET %s",
                tableConfig.getTargetTable(),
                insertColumns,
                placeholders,
                geometryPlaceholder,
                targetPk,
                updateSet
        );

        try {
            geoTargetJdbcTemplate.batchUpdate(sql, items, items.size(), (ps, item) -> {
                int index = 1;
                for (String column : sourceColumns) {
                    ps.setObject(index++, item.getAttribute(column));
                }
                ps.setString(index, item.getGeometryGeoJson());
            });
        } catch (DataAccessException e) {
            log.error("Critical failure during geo-target UPSERT for {}",
                    tableConfig.getTargetTable(), e);
            throw new RuntimeException(
                    "Error executing geo-target UPSERT for "
                            + tableConfig.getTargetTable() + ": " + e.getMessage(),
                    e);
        }
    }

    public static void requirePositiveSrid(JobTableConfig tableConfig) {
        if (tableConfig.getSrid() <= 0) {
            throw new IllegalArgumentException(
                    "srid must be a positive integer in the job YAML for table "
                            + tableConfig.getTargetTable()
                            + " (got " + tableConfig.getSrid() + ")");
        }
    }
}
