package br.car.dsp_batch.batch.config.table;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Table configuration for Area of Interest, with date range
 * used in detecting changes by range ({@code DATE_RANGE}).
 */
@Getter
@Setter
public class AreaOfInterestTableProperties extends AdministrativeUnitTableProperties {
    private LocalDate startDate;
    private LocalDate endDate;
}
