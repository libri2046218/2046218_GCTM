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

    @Bean
    public CommandLineRunner testActiveMQ(JmsTemplate jmsTemplate) {
        return args -> {

            System.out.println("🚀 [SENDER] Initializing test message...");

            // Create a dummy record using your UnifiedEnvelope
            UnifiedEnvelope testMsg = new UnifiedEnvelope(
                    new UnifiedEnvelope.Header(java.util.UUID.randomUUID(), java.time.Instant.now(),
                            UnifiedEnvelope.MsgType.TELEMETRY, "INGESTOR-TEST-SERVICE", null, null),
                    new UnifiedEnvelope.Payload("ROVER-01", UnifiedEnvelope.Status.ok,
                            java.util.List.of(new UnifiedEnvelope.Metric("battery", 88.5, "%")), null)
            );

            // Send to the Topic
            jmsTemplate.setPubSubDomain(true);
            jmsTemplate.convertAndSend("mars.telemetry.topic", testMsg);

            System.out.println("✅ [SENDER] Message sent successfully!");
        };
    }
}
