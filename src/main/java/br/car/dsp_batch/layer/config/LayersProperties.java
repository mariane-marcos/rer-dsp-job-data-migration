package br.car.dsp_batch.layer.config;

import br.car.dsp_batch.layer.metadata.QualifiedTable;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Geographic layers configured via YAML ({@code batch.layers}).
 */
@ConfigurationProperties(prefix = "batch")
@Getter
@Setter
public class LayersProperties {

    private List<LayerConfig> layers = new ArrayList<>();

    public List<LayerConfig> enabledLayers() {
        return layers.stream()
                .filter(LayerConfig::isEnabled)
                .toList();
    }

    public void validate() {
        Set<String> keys = new HashSet<>();
        for (LayerConfig layer : layers) {
            requireQualifiedTable("source-table", layer.getSourceTable());
            requireNonBlank("primary-key", layer.getPrimaryKey());
            requireNonBlank("area-of-interest-id-column", layer.getAreaOfInterestIdColumn());
            requireNonBlank("updated-at-column", layer.getUpdatedAtColumn());
            requireNonBlank("label-column", layer.getLabelColumn());
            requireNonBlank("geometry-column", layer.getGeometryColumn());
            validatePersistColumns(layer);

            String key = layer.resolveKey();
            if (!keys.add(key)) {
                throw new IllegalStateException(
                        "Duplicate target table 'dsp."
                                + layer.resolveSourceTable().table()
                                + "' for batch.layers (same table name from different sources)");
            }
        }
    }

    private static void validatePersistColumns(LayerConfig layer) {
        List<String> persistColumns = layer.getPersistColumns();
        if (persistColumns == null || persistColumns.isEmpty()) {
            return;
        }

        Set<String> requiredSource = Set.of(
                layer.getPrimaryKey().trim(),
                layer.getAreaOfInterestIdColumn().trim(),
                layer.getUpdatedAtColumn().trim(),
                layer.getLabelColumn().trim(),
                layer.getGeometryColumn().trim()
        );

        Set<String> seen = new HashSet<>();
        for (String raw : persistColumns) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException(
                        "batch.layers: persist-columns must not contain blank entries"
                                + " (source-table=" + layer.getSourceTable() + ")");
            }
            String column = raw.trim();
            if (!seen.add(column)) {
                throw new IllegalStateException(
                        "batch.layers: duplicate persist-columns entry '" + column
                                + "' (source-table=" + layer.getSourceTable() + ")");
            }
            if (requiredSource.contains(column)) {
                throw new IllegalStateException(
                        "batch.layers: persist-columns entry '" + column
                                + "' duplicates a required column mapping"
                                + " (source-table=" + layer.getSourceTable() + ")");
            }
            String lower = column.toLowerCase(Locale.ROOT);
            if (LayerConfig.CANONICAL_TARGET_COLUMNS.contains(lower)
                    || LayerConfig.CANONICAL_TARGET_COLUMNS.contains(column)) {
                throw new IllegalStateException(
                        "batch.layers: persist-columns entry '" + column
                                + "' collides with a canonical target column name "
                                + LayerConfig.CANONICAL_TARGET_COLUMNS
                                + " (source-table=" + layer.getSourceTable() + ")");
            }
        }
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "batch.layers: '" + field + "' is required");
        }
    }

    private static void requireQualifiedTable(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "batch.layers: '" + field + "' is required (schema.table format)");
        }
        try {
            QualifiedTable.parse(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "batch.layers: invalid '" + field + "' value '" + value + "'. "
                            + ex.getMessage(),
                    ex);
        }
    }
}
