package br.car.dsp_batch.batch.config.table;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds and validates {@link AreaOfInterestTableProperties}.
 */
@Configuration
public class AreaOfInterestTableConfigs {

    @Bean(name = "areaOfInterestTableConfig")
    @ConfigurationProperties(prefix = "batch.area-of-interest")
    public AreaOfInterestTableProperties areaOfInterestTableConfig() {
        return new AreaOfInterestTableProperties();
    }
}
