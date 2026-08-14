package br.car.dsp_batch.sync;

import br.car.dsp_batch.batch.partitioner.ColumnRangePartitioner;
import br.car.dsp_batch.temporal.WatermarkColumnSpec;
import org.springframework.batch.core.partition.support.Partitioner;

import javax.sql.DataSource;
import java.time.Instant;

/**
 * Shared helpers for watermark-aware range partitioning.
 */
public final class WatermarkPartitionSupport {

    private WatermarkPartitionSupport() {
    }

    public static String resolveWhereClause(String configWhere,
                                            WatermarkColumnSpec watermarkColumn,
                                            Instant watermark) {
        return WatermarkSql.combineWhere(
                configWhere,
                WatermarkSql.buildUpdatedAtFilter(watermarkColumn, watermark)
        );
    }

    public static Partitioner columnRangePartitioner(DataSource sourceDataSource,
                                                     String sourceTable,
                                                     String partitionColumn,
                                                     String whereClause) {
        return new ColumnRangePartitioner(
                sourceDataSource,
                sourceTable,
                partitionColumn,
                whereClause
        );
    }
}
