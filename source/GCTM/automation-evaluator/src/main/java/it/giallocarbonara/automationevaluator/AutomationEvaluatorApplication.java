package it.giallocarbonara.automationevaluator;

import it.giallocarbonara.SensorData;
import it.giallocarbonara.UnifiedEnvelope;
import it.giallocarbonara.automationevaluator.entity.AutomationRule;
import it.giallocarbonara.automationevaluator.repository.RuleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.JmsListener;

@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
public class AutomationEvaluatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomationEvaluatorApplication.class, args);
    }

}
