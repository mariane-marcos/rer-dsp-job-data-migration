package br.car.dsp_batch.batch.config.table;

import br.car.dsp_batch.sync.SyncKeys;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;

import java.time.LocalDate;

/**
 * Table configuration for Area of Interest.
 * Supports {@code DATE_RANGE} and {@code WATERMARK} change detection strategies.
 */
@Getter
@Setter
public class AreaOfInterestTableProperties extends AdministrativeUnitTableProperties
        implements InitializingBean {

    private LocalDate startDate;
    private LocalDate endDate;

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    @Override
    public String getSyncKey() {
        String configured = super.getSyncKey();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return SyncKeys.AREA_OF_INTEREST;
    }

    public void validate() {
        if (getChangeDetectionStrategy() == br.car.dsp_batch.batch.config.strategy.ChangeDetectionStrategyType.WATERMARK) {
            if (getUpdatedAtColumn() == null || getUpdatedAtColumn().isBlank()) {
                throw new IllegalStateException(
                        "batch.area-of-interest: 'updated-at-column' is required when "
                                + "change-detection-strategy is WATERMARK");
            }
        }
    }
}
