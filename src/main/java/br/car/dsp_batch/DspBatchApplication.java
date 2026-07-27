package br.car.dsp_batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DspBatchApplication {

	/**
	 * Encerra a JVM após o JobRunner — necessário para o container one-shot
	 * do core (docker compose run) não ficar "travado" e o start.sh seguir
	 * para backend/frontend.
	 */
	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(DspBatchApplication.class, args)));
	}

}
