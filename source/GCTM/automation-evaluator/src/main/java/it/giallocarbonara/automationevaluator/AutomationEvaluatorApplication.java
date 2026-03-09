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

}
