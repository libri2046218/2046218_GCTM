package it.giallocarbonara.webengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
public class WebEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebEngineApplication.class, args);
    }

}
