package br.car.dsp_batch.batch.config.table;

import br.car.dsp_batch.sync.SyncKeys;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Table configuration for Area of Interest.
 * Supports {@code DATE_RANGE} and {@code WATERMARK} change detection strategies.
 */
@Getter
@Setter
public class AreaOfInterestTableProperties extends AdministrativeUnitTableProperties {

    private LocalDate startDate;
    private LocalDate endDate;

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
    }

    @Override
    public String getSyncKey() {
        String configured = super.getSyncKey();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return SyncKeys.AREA_OF_INTEREST;
    }

    /** Alias used by validation tests. */
    public void validate() {
        validateWatermarkConfig();
    }
}
