package br.car.dsp_batch.batch.config.table;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategyType;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Externalized {@link JobTableConfig} bound from application properties.
 */
@Getter
@Setter
public class AdministrativeUnitTableProperties implements JobTableConfig {

    private String sourceTable;
    private String targetTable;
    private String primaryKey;
    private String partitionColumn;
    private String geometryColumn;
    private String whereClause = "1=1";
    private List<String> comparisonColumns = new ArrayList<>();
    private List<String> persistColumns = new ArrayList<>();
    /** Persisted only on dsp-db (not exhibition), e.g. theme_1…theme_4. */
    private List<String> businessOnlyPersistColumns = new ArrayList<>();
    private String layerName;
    private Map<String, String> columnMapping = new HashMap<>();
    private int srid;
    private ChangeDetectionStrategyType changeDetectionStrategy = ChangeDetectionStrategyType.DEFAULT;
    private String updatedAtColumn;
    private String syncKey;

    @Override
    public String getPartitionColumn() {
        return partitionColumn != null && !partitionColumn.isBlank()
                ? partitionColumn
                : primaryKey;
    }
}
