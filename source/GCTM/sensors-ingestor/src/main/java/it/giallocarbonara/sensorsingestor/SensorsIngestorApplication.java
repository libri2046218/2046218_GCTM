package it.giallocarbonara.sensorsingestor;

import it.giallocarbonara.UnifiedEnvelope;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.core.JmsTemplate;

@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
public class SensorsIngestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SensorsIngestorApplication.class, args);
    }

}
