package it.giallocarbonara.automationevaluator;

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
    public void onMessage(UnifiedEnvelope message) {
        System.out.println("📥 [RECEIVER] SUCCESS! Received data from: " + message.header().origin());
        System.out.println("📊 Metric: " + message.payload().metrics().get(0).name() +
                " = " + message.payload().metrics().get(0).value());
    }
}
