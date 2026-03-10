package it.giallocarbonara.automationevaluator;

import it.giallocarbonara.SensorData;
import it.giallocarbonara.UnifiedEnvelope;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.JmsListener;

@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
public class AutomationEvaluatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomationEvaluatorApplication.class, args);
    }
    @JmsListener(destination = "sensors.topic")
    public void onMessage(SensorData sensorData) {
        System.out.println("📥 [RECEIVER] SUCCESS! Received data from: " + sensorData.header().sender());
        System.out.println("📊 Metric: " + sensorData.metrics().getFirst().name() +
                " = " + sensorData.metrics().getFirst().value());
    }
}
