package it.giallocarbonara.actuatormanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper; // Fondamentale per risolvere l'errore mapper
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

        UnifiedEnvelope testReq = new UnifiedEnvelope(
                new UnifiedEnvelope.Header(UUID.randomUUID(), Instant.now(), UnifiedEnvelope.MsgType.RPC_REQUEST, "self-test", null, null),
                new UnifiedEnvelope.Payload(target, UnifiedEnvelope.Status.ok, List.of(new UnifiedEnvelope.Metric("state", state, "bool")), null)
        );

        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("actuators.topic", testReq);
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

    @JmsListener(destination = "actuators.topic")
    public void handleCommand(UnifiedEnvelope request) {
        // Gestiamo solo le Request
        if (request.header().msg_type() != UnifiedEnvelope.MsgType.RPC_REQUEST) return;

        String actuatorName = request.payload().subject_id();

        // Estrazione sicura dello stato dalle metriche
        String newState = request.payload().metrics().stream()
                .filter(m -> m.name().equalsIgnoreCase("state") || m.name().equalsIgnoreCase("value"))
                .map(m -> m.value().toString().toUpperCase())
                .findFirst()
                .orElse("OFF");

        try {
            // Chiamata POST al simulatore
            Map<String, String> body = Map.of("state", newState);
            restTemplate.postForObject(SIMULATOR_URL + actuatorName, body, String.class);

            System.out.println("⚙️ [ACTUATOR] Success: " + actuatorName + " is now " + newState);

            // Invio Risposta di successo
            sendResponse(request, UnifiedEnvelope.Status.SUCCESS, "Actuator " + actuatorName + " set to " + newState);

        } catch (Exception e) {
            System.err.println("❌ [ACTUATOR ERROR] " + e.getMessage());
            sendResponse(request, UnifiedEnvelope.Status.FAILED, e.getMessage());
        }
    }

    private void sendResponse(UnifiedEnvelope request, UnifiedEnvelope.Status status, String info) {
        UnifiedEnvelope response = new UnifiedEnvelope(
                new UnifiedEnvelope.Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        UnifiedEnvelope.MsgType.RPC_RESPONSE,
                        "actuator-manager",
                        request.header().msg_id().toString(), // Correlation ID per il chiamante
                        null
                ),
                new UnifiedEnvelope.Payload(
                        request.payload().subject_id(),
                        status,
                        List.of(new UnifiedEnvelope.Metric("info", info, "text")),
                        null
                )
        );

        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("actuators.topic", response);
    }
}