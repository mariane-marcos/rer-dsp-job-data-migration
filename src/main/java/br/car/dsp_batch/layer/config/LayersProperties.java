package br.car.dsp_batch.layer.config;

import br.car.dsp_batch.layer.metadata.QualifiedTable;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Geographic layers (camadas) configured via YAML ({@code batch.layers}).
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
            requireNonBlank("area-of-interest-id-column", layer.getAreaOfInterestIdColumn());

            String key = layer.resolveKey();
            if (!keys.add(key)) {
                throw new IllegalStateException(
                        "Duplicate target table 'dsp."
                                + layer.resolveSourceTable().table()
                                + "' for batch.layers (same table name from different sources)");
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
