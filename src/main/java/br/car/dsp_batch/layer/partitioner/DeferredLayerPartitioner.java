package br.car.dsp_batch.layer.partitioner;

import br.car.dsp_batch.batch.partitioner.ColumnRangePartitioner;
import br.car.dsp_batch.layer.metadata.LayerMetadataRegistry;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Creates the {@link ColumnRangePartitioner} at execution time,
 * after the setup step has populated the {@link LayerMetadataRegistry}.
 */
public class DeferredLayerPartitioner implements Partitioner {

    private final LayerMetadataRegistry registry;
    private final String layerKey;
    private final DataSource sourceDataSource;

    public DeferredLayerPartitioner(LayerMetadataRegistry registry,
                                           String layerKey,
                                           DataSource sourceDataSource) {
        this.registry = registry;
        this.layerKey = layerKey;
        this.sourceDataSource = sourceDataSource;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        LayerTableMetadata metadata = registry.getRequired(layerKey);
        String where = metadata.whereClause();
        String whereClause = "1=1".equals(where) ? null : where;

        ColumnRangePartitioner delegate = new ColumnRangePartitioner(
                sourceDataSource,
                metadata.qualifiedSourceTable(),
                metadata.primaryKeyColumn(),
                whereClause
        );
        return new LayerPartitioner(delegate, layerKey).partition(gridSize);
    }
}
