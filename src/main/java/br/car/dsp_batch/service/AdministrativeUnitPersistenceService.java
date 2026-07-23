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
 * Config-driven persistence for administrative unit geographic records.
 */
@Slf4j
@Service
public class AdministrativeUnitPersistenceService {

    private final JdbcTemplate targetJdbcTemplate;

    public AdministrativeUnitPersistenceService(
            @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate) {
        this.targetJdbcTemplate = targetJdbcTemplate;
    }

    public void upsertAll(List<AdministrativeUnitDTO> items, JobTableConfig tableConfig) {
        if (items == null || items.isEmpty()) {
            return;
        }

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
                    "Upserted 0 records into {} (skipped {} without geometry)",
                    tableConfig.getTargetTable(),
                    skippedWithoutGeometry
            );
            return;
        }

        List<String> sourceColumns = tableConfig.getPersistColumns();
        List<String> targetColumns = sourceColumns.stream()
                .map(tableConfig::resolveTargetColumn)
                .collect(Collectors.toList());
        String targetGeom = tableConfig.resolveTargetColumn(tableConfig.getGeometryColumn());
        String targetPk = tableConfig.resolveTargetColumn(tableConfig.getPrimaryKey());

        String insertColumns = String.join(", ", targetColumns) + ", " + targetGeom;
        String placeholders = sourceColumns.stream()
                .map(c -> "?")
                .collect(Collectors.joining(", "));
        String geometryPlaceholder = String.format(
                "public.ST_SetSRID(public.ST_GeomFromGeoJSON(?), %d)",
                tableConfig.getSrid()
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
            targetJdbcTemplate.batchUpdate(sql, validItems, validItems.size(), (ps, item) -> {
                int index = 1;
                for (String column : sourceColumns) {
                    ps.setObject(index++, item.getAttribute(column));
                }
                ps.setString(index, item.getGeometryGeoJson());
            });
            log.info(
                    "Upserted {} records into {} (skipped {} without geometry)",
                    validItems.size(),
                    tableConfig.getTargetTable(),
                    skippedWithoutGeometry
            );
        } catch (DataAccessException e) {
            log.error("Critical failure during batch UPSERT for {}", tableConfig.getTargetTable(), e);
            throw new RuntimeException(
                    "Error executing batch UPSERT for " + tableConfig.getTargetTable() + ": " + e.getMessage(),
                    e);
        }
    }
}
