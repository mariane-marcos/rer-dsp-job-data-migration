package br.car.dsp_batch.sync;

import java.time.Instant;

/**
 * SQL fragments for incremental watermark filtering on source update columns.
 */
public final class WatermarkSql {

    private WatermarkSql() {
    }

    /**
     * Builds a WHERE fragment for the source update column.
     * Always requires {@code IS NOT NULL}. With a watermark, restricts to the delta.
     */
    public static String buildUpdatedAtFilter(String updatedAtSourceColumn, Instant watermark) {
        String notNull = updatedAtSourceColumn + " IS NOT NULL";
        if (watermark == null) {
            return notNull;
        }
        return notNull + " AND " + updatedAtSourceColumn + " > TIMESTAMP WITH TIME ZONE '"
                + watermark + "'";
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
