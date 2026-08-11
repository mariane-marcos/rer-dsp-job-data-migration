package br.car.dsp_batch.layer.partitioner;

import br.car.dsp_batch.batch.partitioner.ColumnRangePartitioner;
import br.car.dsp_batch.layer.metadata.LayerMetadataRegistry;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.service.LayerChangeDetectionService;
import br.car.dsp_batch.layer.sync.LayerSyncStateRepository;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;

/**
 * Creates the {@link ColumnRangePartitioner} at execution time,
 * after the setup step has populated the {@link LayerMetadataRegistry}.
 * Applies the same {@code updated_at} watermark filter used by the reader.
 */
public class DeferredLayerPartitioner implements Partitioner {

    private final LayerMetadataRegistry registry;
    private final String layerKey;
    private final DataSource sourceDataSource;
    private final LayerSyncStateRepository syncStateRepository;

    public DeferredLayerPartitioner(LayerMetadataRegistry registry,
                                    String layerKey,
                                    DataSource sourceDataSource,
                                    LayerSyncStateRepository syncStateRepository) {
        this.registry = registry;
        this.layerKey = layerKey;
        this.sourceDataSource = sourceDataSource;
        this.syncStateRepository = syncStateRepository;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        LayerTableMetadata metadata = registry.getRequired(layerKey);
        Instant watermark = syncStateRepository.findWatermark(layerKey).orElse(null);

        String whereClause = combineWhere(
                metadata.whereClause(),
                LayerChangeDetectionService.buildUpdatedAtFilterSql(
                        metadata.updatedAtSourceColumn(), watermark)
        );

        ColumnRangePartitioner delegate = new ColumnRangePartitioner(
                sourceDataSource,
                metadata.qualifiedSourceTable(),
                metadata.primaryKeyColumn(),
                whereClause
        );
        return new LayerPartitioner(delegate, layerKey).partition(gridSize);
    }

    static String combineWhere(String configWhere, String updatedAtFilter) {
        boolean hasConfigWhere = configWhere != null
                && !configWhere.isBlank()
                && !"1=1".equals(configWhere.trim());

        if (!hasConfigWhere && updatedAtFilter == null) {
            return null;
        }
        if (hasConfigWhere && updatedAtFilter == null) {
            return configWhere;
        }
        if (!hasConfigWhere) {
            return updatedAtFilter;
        }
        return "(" + configWhere + ") AND " + updatedAtFilter;
    }
}
