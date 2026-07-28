package br.car.dsp_batch.batch.config.strategy;

/**
 * Available change-detection strategies.
 * New strategies can be added without changing the main batch configuration.
 */
public enum ChangeDetectionStrategyType {
    DEFAULT,
    /** Filters comparison columns within the {@code startDate}–{@code endDate} range. */
    DATE_RANGE
}
