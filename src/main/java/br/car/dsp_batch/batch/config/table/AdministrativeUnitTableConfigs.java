package br.car.dsp_batch.batch.config.table;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized {@link br.car.dsp_batch.batch.config.JobTableConfig} beans for each
 * administrative unit level.
 */
@Configuration
public class AdministrativeUnitTableConfigs {

    @Bean(name = "adminUnitLevel1TableConfig")
    @ConfigurationProperties(prefix = "batch.admin-unit.level-1")
    public AdministrativeUnitTableProperties adminUnitLevel1TableConfig() {
        return new AdministrativeUnitTableProperties();
    }

    @Bean(name = "adminUnitLevel2TableConfig")
    @ConfigurationProperties(prefix = "batch.admin-unit.level-2")
    public AdministrativeUnitTableProperties adminUnitLevel2TableConfig() {
        return new AdministrativeUnitTableProperties();
    }

    @Bean(name = "adminUnitLevel3TableConfig")
    @ConfigurationProperties(prefix = "batch.admin-unit.level-3")
    public AdministrativeUnitTableProperties adminUnitLevel3TableConfig() {
        return new AdministrativeUnitTableProperties();
    }
}
