package it.giallocarbonara.actuatormanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper; // Fondamentale per risolvere l'errore mapper
import it.giallocarbonara.ActuatorCommand;
import it.giallocarbonara.ActuatorStatus;
import it.giallocarbonara.Header;
import it.giallocarbonara.UnifiedEnvelope;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
@EnableScheduling
@RestController
public class ActuatorManagerApplication {

    // DICHIARAZIONE DEL MAPPER PER RISOLVERE L'ERRORE
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final JmsTemplate jmsTemplate;
    private final String SIMULATOR_URL = "http://mars-simulator:8080/api/actuators";

    public ActuatorManagerApplication(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(ActuatorManagerApplication.class, args);
    }

    @GetMapping("/api/actuators/status")
    public JsonNode getActuatorsStatus() {
        try {
            return restTemplate.getForObject(SIMULATOR_URL, JsonNode.class);
        } catch (Exception e) {
            // Se il simulatore è giù, restituiamo un JSON di errore usando il mapper
            return mapper.createObjectNode().put("error", "Simulatore offline: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 2000)
    public void autoTestActuators() {
        String target = "cooling_fan";
        String state = Math.random() > 0.5 ? "ON" : "OFF";

        System.out.println("🧪 [AUTO-TEST] Sending RPC: " + target + " -> " + state);

        ActuatorCommand test = new ActuatorCommand(
                new Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        "self-test",
                        null,
                        null
                ),
                target,
                state
        );

        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("command.actuators.topic", test);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

@Service
class ActuatorService {

    private final RestTemplate restTemplate;
    private final JmsTemplate jmsTemplate;
    private final String SIMULATOR_URL = "http://mars-simulator:8080/api/actuators/";

    public ActuatorService(RestTemplate restTemplate, JmsTemplate jmsTemplate) {
        this.restTemplate = restTemplate;
        this.jmsTemplate = jmsTemplate;
    }

    @JmsListener(destination = "command.actuators.topic")
    public void handleCommand(ActuatorCommand request) {

        String actuatorName = request.actuator_id();

        // Estrazione sicura dello stato dalle metriche
        String newState = request.desired_state();

        try {
            // Chiamata POST al simulatore
            Map<String, String> body = Map.of("state", newState);
            restTemplate.postForObject(SIMULATOR_URL + actuatorName, body, String.class);

            System.out.println("⚙️ [ACTUATOR] Success: " + actuatorName + " is now " + newState);

        } catch (Exception e) {
            System.err.println("❌ [ACTUATOR ERROR] " + e.getMessage());
        }

        checkStatus(actuatorName, null, null);
        //TODO: da migliorare inserendo correlationId e requester ma solo dopo aver modificato in tutte le branch il DTO dell'header
    }

    //@Fede chiamami che ti spiego (sia checkstatus che topics) -@PF
    public void checkStatus(String actuatorId, String correlationId, String requester) {
        //TODO: Chiamata per raccogliere lo stato dell'attuatore
        String actual_state = null;
        Instant updated_at = null;

        ActuatorStatus status = new ActuatorStatus(
                new Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        "actuator-manager",
                        correlationId,
                        requester
                ),
                actuatorId,
                actual_state,
                updated_at
        );

        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("status.actuators.topic", status);

    }
}