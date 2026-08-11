package br.car.dsp_batch.batch.partitioner;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategyType;
import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.sync.WatermarkSql;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;

/**
 * Resolves the partition WHERE clause at execution time so watermark lookups
 * happen when the job runs, not during application context startup.
 */
public class DeferredWatermarkPartitioner implements Partitioner {

    private final DataSource sourceDataSource;
    private final JobTableConfig tableConfig;
    private final SyncStateRepository syncStateRepository;

    public DeferredWatermarkPartitioner(DataSource sourceDataSource,
                                        JobTableConfig tableConfig,
                                        SyncStateRepository syncStateRepository) {
        this.sourceDataSource = sourceDataSource;
        this.tableConfig = tableConfig;
        this.syncStateRepository = syncStateRepository;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        String whereClause = resolveWhereClause();
        ColumnRangePartitioner delegate = new ColumnRangePartitioner(
                sourceDataSource,
                tableConfig.getSourceTable(),
                tableConfig.getPartitionColumn(),
                whereClause
        );
        return delegate.partition(gridSize);
    }

    private String resolveWhereClause() {
        if (tableConfig.getChangeDetectionStrategy() != ChangeDetectionStrategyType.WATERMARK) {
            String where = tableConfig.getWhereClause();
            return "1=1".equals(where) ? null : where;
        }
        Instant watermark = syncStateRepository.findWatermark(tableConfig.getSyncKey()).orElse(null);
        return WatermarkSql.combineWhere(
                tableConfig.getWhereClause(),
                WatermarkSql.buildUpdatedAtFilter(tableConfig.getUpdatedAtColumn(), watermark)
        );
    }
}
