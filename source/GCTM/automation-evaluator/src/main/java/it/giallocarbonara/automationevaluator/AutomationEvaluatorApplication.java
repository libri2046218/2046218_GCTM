package it.giallocarbonara.automationevaluator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
public class AutomationEvaluatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomationEvaluatorApplication.class, args);
    }

}
