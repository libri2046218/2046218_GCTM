package it.giallocarbonara.sensorsingestor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
public class SensorsIngestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SensorsIngestorApplication.class, args);
    }

}