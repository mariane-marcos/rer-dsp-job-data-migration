package br.car.dsp_batch.batch.config.table;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean de {@link br.car.dsp_batch.batch.config.JobTableConfig} para Rural Property.
 */
@Configuration
public class RuralPropertyTableConfigs {

    @Bean(name = "ruralPropertyTableConfig")
    @ConfigurationProperties(prefix = "batch.rural-property")
    public RuralPropertyTableProperties ruralPropertyTableConfig() {
        return new RuralPropertyTableProperties();
    }
}
