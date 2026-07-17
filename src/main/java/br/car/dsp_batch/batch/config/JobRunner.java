package br.car.dsp_batch.batch.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Optionally launches configured administrative unit jobs on application startup.
 */
@Slf4j
@Component
public class JobRunner implements CommandLineRunner {

    private final JobLauncher jobLauncher;
    private final Job adminUnitLevel1GeoserverJob;
    private final Job adminUnitLevel2GeoserverJob;
    private final Job adminUnitLevel3GeoserverJob;
    private final Job ruralPropertyGeoserverJob;

    @Value("${execution-jobs.admin-unit-level-1-geoserver-job:true}")
    private boolean runLevel1;

    @Value("${execution-jobs.admin-unit-level-2-geoserver-job:false}")
    private boolean runLevel2;

    @Value("${execution-jobs.admin-unit-level-3-geoserver-job:false}")
    private boolean runLevel3;

    @Value("${execution-jobs.rural-property-geoserver-job:false}")
    private boolean runRuralProperty;

    public JobRunner(
            JobLauncher jobLauncher,
            @Qualifier("adminUnitLevel1GeoserverJob") Job adminUnitLevel1GeoserverJob,
            @Qualifier("adminUnitLevel2GeoserverJob") Job adminUnitLevel2GeoserverJob,
            @Qualifier("adminUnitLevel3GeoserverJob") Job adminUnitLevel3GeoserverJob,
            @Qualifier("ruralPropertyGeoserverJob") Job ruralPropertyGeoserverJob) {
        this.jobLauncher = jobLauncher;
        this.adminUnitLevel1GeoserverJob = adminUnitLevel1GeoserverJob;
        this.adminUnitLevel2GeoserverJob = adminUnitLevel2GeoserverJob;
        this.adminUnitLevel3GeoserverJob = adminUnitLevel3GeoserverJob;
        this.ruralPropertyGeoserverJob = ruralPropertyGeoserverJob;
    }

    @Override
    public void run(String... args) throws Exception {
        runJobIfEnabled(runLevel1, adminUnitLevel1GeoserverJob);
        runJobIfEnabled(runLevel2, adminUnitLevel2GeoserverJob);
        runJobIfEnabled(runLevel3, adminUnitLevel3GeoserverJob);
        runJobIfEnabled(runRuralProperty, ruralPropertyGeoserverJob);
    }

    private void runJobIfEnabled(boolean enabled, Job job) throws Exception {
        if (!enabled) {
            log.info("Job {} is disabled", job.getName());
            return;
        }
        log.info("Starting job {}", job.getName());
        jobLauncher.run(job, new org.springframework.batch.core.JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters());
    }
}
