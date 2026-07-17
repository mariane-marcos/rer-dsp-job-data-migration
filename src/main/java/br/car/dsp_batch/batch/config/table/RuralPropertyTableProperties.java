package br.car.dsp_batch.batch.config.table;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Configuração de tabela para Rural Property, com intervalo de datas
 * usado na detecção de alterações por faixa ({@code DATE_RANGE}).
 */
@Getter
@Setter
public class RuralPropertyTableProperties extends AdministrativeUnitTableProperties {

    private LocalDate startDate;
    private LocalDate endDate;
}
