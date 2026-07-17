package br.car.dsp_batch.batch.config.strategy;

/**
 * Estratégias disponíveis de detecção de alterações.
 * Novas estratégias podem ser adicionadas sem alterar a configuração principal do batch.
 */
public enum ChangeDetectionStrategyType {
    DEFAULT,
    /** Filtra colunas de comparação no intervalo {@code startDate}–{@code endDate}. */
    DATE_RANGE
}
