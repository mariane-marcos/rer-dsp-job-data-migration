package br.car.dsp_batch.batch.reader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.database.JdbcPagingItemReader;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Base paging reader configured for a single ID partition range.
 */
@Slf4j
public abstract class AbstractPartitionedPagingItemReader<T> extends JdbcPagingItemReader<T> {

    protected AbstractPartitionedPagingItemReader(DataSource dataSource, Long minId, Long maxId, int pageSize) {
        super();
        if (minId != null && maxId != null) {
            log.info("Configuring partitioned reader for ID range: {}-{}", minId, maxId);
        }

        this.setDataSource(dataSource);
        this.setPageSize(pageSize);

        if (minId != null && maxId != null) {
            Map<String, Object> parameterValues = new HashMap<>();
            parameterValues.put("minId", minId);
            parameterValues.put("maxId", maxId);
            this.setParameterValues(parameterValues);
        }
    }
}
