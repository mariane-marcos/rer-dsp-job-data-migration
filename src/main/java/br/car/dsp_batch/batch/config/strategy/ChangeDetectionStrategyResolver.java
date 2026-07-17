package br.car.dsp_batch.batch.config.strategy;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a {@link ChangeDetectionStrategy} by type.
 * New strategies are auto-registered when annotated as Spring components.
 */
@Component
public class ChangeDetectionStrategyResolver {

    private final Map<ChangeDetectionStrategyType, ChangeDetectionStrategy> strategies;

    public ChangeDetectionStrategyResolver(List<ChangeDetectionStrategy> strategyList) {
        this.strategies = new EnumMap<>(ChangeDetectionStrategyType.class);
        for (ChangeDetectionStrategy strategy : strategyList) {
            strategies.put(strategy.getType(), strategy);
        }
    }

    public ChangeDetectionStrategy resolve(ChangeDetectionStrategyType type) {
        return Optional.ofNullable(strategies.get(type))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No change detection strategy registered for type: " + type));
    }
}
