package br.car.dsp_batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DspBatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(DspBatchApplication.class, args);
	}

}
