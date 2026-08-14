package br.car.dsp_batch.sync;

import br.car.dsp_batch.temporal.WatermarkColumnSpec;
import br.car.dsp_batch.temporal.WatermarkPredicate;
import br.car.dsp_batch.temporal.WatermarkTemporalBridge;

import java.time.Instant;

/**
 * SQL fragments for incremental watermark filtering on source update columns.
 * Delegates conversion rules to {@link WatermarkTemporalBridge}.
 */
public final class WatermarkSql {

    private WatermarkSql() {
    }

    /**
     * Builds a WHERE fragment for the source update column.
     * Always requires {@code IS NOT NULL}. With a watermark, restricts to the delta
     * using the same Instant↔source projection as persistence.
     */
    public static String buildUpdatedAtFilter(WatermarkColumnSpec watermarkColumn,
                                              Instant watermark) {
        return WatermarkTemporalBridge.buildPredicate(watermarkColumn, watermark).sqlFragment();
    }

    public static WatermarkPredicate buildPredicate(WatermarkColumnSpec watermarkColumn,
                                                    Instant watermark) {
        return WatermarkTemporalBridge.buildPredicate(watermarkColumn, watermark);
    }

    /**
     * Combines an optional config WHERE clause with an optional update-column filter.
     */
    public static String combineWhere(String configWhere, String updatedAtFilter) {
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
