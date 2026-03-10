package it.giallocarbonara.actuatormanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.giallocarbonara.ActuatorCommand;
import it.giallocarbonara.ActuatorStatus;
import it.giallocarbonara.Header;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
@EnableScheduling
@RestController
public class ActuatorManagerApplication {

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

    /**
     * Esegue il broadcast dello stato di tutti gli attuatori SOLO all'avvio.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void broadcastInitialStatus() {
        broadcastAllStatuses(null, null, "STARTUP");
    }

    /**
     * Esegue la lettura dello stato globale e pubblica un ActuatorStatus per ogni attuatore.
     */
    public void broadcastAllStatuses(String correlationId, String requester, String reason) {
        try {
            String jsonRaw = restTemplate.getForObject(SIMULATOR_URL, String.class);
            if (jsonRaw == null) {
                return;
            }

            JsonNode root = mapper.readTree(jsonRaw);
            JsonNode actuatorsNode = root.path("actuators");

            if (actuatorsNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = actuatorsNode.properties().iterator();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    sendAsActuatorStatus(entry.getKey(), entry.getValue().asText("UNKNOWN"), correlationId, requester);
                }
            } else {
                // Backward-compatible parsing for old simulator payloads.
                Iterator<Map.Entry<String, JsonNode>> fields = root.properties().iterator();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String state = entry.getValue().isTextual()
                            ? entry.getValue().asText("UNKNOWN")
                            : entry.getValue().path("state").asText("UNKNOWN");
                    sendAsActuatorStatus(entry.getKey(), state, correlationId, requester);
                }
            }

            System.out.println("📢 [" + reason + "] Broadcast of all actuators completed.");
        } catch (Exception e) {
            System.err.println("❌ [" + reason + " ERROR] " + e.getMessage());
        }
    }

    /**
     * Helper centralizzato per inviare messaggi in formato ActuatorStatus.
     */
    public void sendAsActuatorStatus(String actuatorId, String stateValue, String correlationId, String requester) {

        ActuatorStatus status = new ActuatorStatus(
                new Header(UUID.randomUUID(), Instant.now(), "actuator-manager", correlationId, requester),
                actuatorId,
                stateValue,
                Instant.now()
        );

        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("status.actuators.topic", status);
    }

    @GetMapping("/api/actuators/status")
    public JsonNode getActuatorsStatus() {
        try {
            return restTemplate.getForObject(SIMULATOR_URL, JsonNode.class);
        } catch (Exception e) {
            return mapper.createObjectNode().put("error", "Simulatore offline: " + e.getMessage());
        }
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

@Service
class ActuatorService {

    private static final String STATUS_SYNC_STATE = "STATUS_SYNC";
    private static final String ALL_ACTUATORS = "*";

    private final RestTemplate restTemplate;
    private final JmsTemplate jmsTemplate;
    private final ActuatorManagerApplication actuatorPublisher;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String SIMULATOR_URL = "http://mars-simulator:8080/api/actuators";

    public ActuatorService(RestTemplate restTemplate,
                           JmsTemplate jmsTemplate,
                           ActuatorManagerApplication actuatorPublisher) {
        this.restTemplate = restTemplate;
        this.jmsTemplate = jmsTemplate;
        this.actuatorPublisher = actuatorPublisher;
    }

    @JmsListener(destination = "command.actuators.topic")
    public void handleCommand(ActuatorCommand request) {
        String actuatorName = request.actuator_id();
        String newState = request.desired_state();

        if (isStatusSyncRequest(request)) {
            String correlationId = request.header() != null ? request.header().correlation_id() : null;
            String requester = request.header() != null ? request.header().sender() : null;
            actuatorPublisher.broadcastAllStatuses(correlationId, requester, "SYNC_REQUEST");
            return;
        }

        try {
            Map<String, String> body = Map.of("state", newState);
            restTemplate.postForObject(SIMULATOR_URL + "/" + actuatorName, body, String.class);
            System.out.println("⚙️ [ACTUATOR] Success: " + actuatorName + " is now " + newState);
        } catch (Exception e) {
            System.err.println("❌ [ACTUATOR ERROR] " + e.getMessage());
        }

        // Recuperiamo i campi dell'header del comando per il feedback
        String correlationId = request.header().correlation_id();
        String sender = request.header().sender();

        checkStatus(actuatorName, correlationId, sender);
    }

    /**
     * Recupera la lista globale per evitare l'errore 405 sulla GET del singolo attuatore.
     */
    public void checkStatus(String actuatorId, String correlationId, String requester) {
        try {
            String jsonRaw = restTemplate.getForObject(SIMULATOR_URL, String.class);
            if (jsonRaw != null) {
                JsonNode root = mapper.readTree(jsonRaw);
                String state = extractActuatorState(root, actuatorId);
                if (state != null) {
                    sendFeedback(actuatorId, state, correlationId, requester);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ [CHECK STATUS ERROR] " + e.getMessage());
        }
    }

    private void sendFeedback(String actuatorId, String actualState, String correlationId, String requester) {

        ActuatorStatus status = new ActuatorStatus(
                new Header(UUID.randomUUID(), Instant.now(), "actuator-manager", correlationId, requester),
                actuatorId,
                actualState,
                Instant.now()
        );

        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("status.actuators.topic", status);
        System.out.println("📢 [FEEDBACK] Sent status for " + actuatorId + ": " + actualState);
    }

    private boolean isStatusSyncRequest(ActuatorCommand request) {
        if (request == null) {
            return false;
        }

        return ALL_ACTUATORS.equals(request.actuator_id())
                && STATUS_SYNC_STATE.equalsIgnoreCase(request.desired_state());
    }

    private String extractActuatorState(JsonNode root, String actuatorId) {
        JsonNode nestedState = root.path("actuators").path(actuatorId);
        if (!nestedState.isMissingNode()) {
            return nestedState.asText("UNKNOWN");
        }

        JsonNode directNode = root.path(actuatorId);
        if (directNode.isMissingNode()) {
            return null;
        }

        if (directNode.isTextual()) {
            return directNode.asText("UNKNOWN");
        }

        return directNode.path("state").asText("UNKNOWN");
    }
}