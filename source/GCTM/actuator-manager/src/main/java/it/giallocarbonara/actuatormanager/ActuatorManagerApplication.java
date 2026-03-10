package it.giallocarbonara.actuatormanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.giallocarbonara.ActuatorCommand;
import it.giallocarbonara.Header;
import it.giallocarbonara.UnifiedEnvelope;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
     * Riceve come String per evitare errori di tipo JsonNode durante la conversione JMS.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void broadcastInitialStatus() {
        try {
            String jsonRaw = restTemplate.getForObject(SIMULATOR_URL, String.class);
            if (jsonRaw != null) {
                JsonNode root = mapper.readTree(jsonRaw);
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    // Invio dello stato per ogni singolo attuatore trovato
                    sendAsUnifiedEnvelope(entry.getKey(), entry.getValue(), null, null, "startup_sync");
                }
                System.out.println("📢 [STARTUP] Initial broadcast of all actuators completed.");
            }
        } catch (Exception e) {
            System.err.println("❌ [STARTUP ERROR] " + e.getMessage());
        }
    }

    /**
     * Helper centralizzato per inviare messaggi in formato UnifiedEnvelope.
     * Estrae i valori primitivi per garantire la compatibilità con il broker JMS.
     */
    public void sendAsUnifiedEnvelope(String actuatorId, JsonNode data, String correlationId, String requester, String eventType) {
        String stateValue = data.path("state").asText("UNKNOWN");

        List<UnifiedEnvelope.Metric> metrics = List.of(
                new UnifiedEnvelope.Metric("actual_state", stateValue, "status")
        );

        UnifiedEnvelope.Header header = new UnifiedEnvelope.Header(
                UUID.randomUUID(),
                Instant.now(),
                UnifiedEnvelope.MsgType.TELEMETRY,
                "actuator-manager",
                correlationId,
                requester
        );

        UnifiedEnvelope.Payload payload = new UnifiedEnvelope.Payload(
                actuatorId,
                UnifiedEnvelope.Status.ok,
                metrics,
                Map.of("event_type", eventType)
        );

        UnifiedEnvelope envelope = new UnifiedEnvelope(header, payload);
        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("status.actuators.topic", envelope);
    }

    @GetMapping("/api/actuators/status")
    public JsonNode getActuatorsStatus() {
        try {
            return restTemplate.getForObject(SIMULATOR_URL, JsonNode.class);
        } catch (Exception e) {
            return mapper.createObjectNode().put("error", "Simulatore offline: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 2000)
    public void autoTestActuators() {
        String target = "cooling_fan";
        String state = Math.random() > 0.5 ? "ON" : "OFF";

        ActuatorCommand test = new ActuatorCommand(
                new Header(UUID.randomUUID(), Instant.now(), "self-test", null, null),
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
    private final ObjectMapper mapper = new ObjectMapper();
    private final String SIMULATOR_URL = "http://mars-simulator:8080/api/actuators";

    public ActuatorService(RestTemplate restTemplate, JmsTemplate jmsTemplate) {
        this.restTemplate = restTemplate;
        this.jmsTemplate = jmsTemplate;
    }

    @JmsListener(destination = "command.actuators.topic")
    public void handleCommand(ActuatorCommand request) {
        String actuatorName = request.actuator_id();
        String newState = request.desired_state();

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
                if (root.has(actuatorId)) {
                    // Utilizziamo il metodo di invio pulito dell'applicazione
                    sendFeedback(actuatorId, root.get(actuatorId), correlationId, requester);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ [CHECK STATUS ERROR] " + e.getMessage());
        }
    }

    private void sendFeedback(String actuatorId, JsonNode data, String correlationId, String requester) {
        String actualState = data.path("state").asText("UNKNOWN");

        UnifiedEnvelope envelope = new UnifiedEnvelope(
                new UnifiedEnvelope.Header(UUID.randomUUID(), Instant.now(), UnifiedEnvelope.MsgType.TELEMETRY, "actuator-manager", correlationId, requester),
                new UnifiedEnvelope.Payload(actuatorId, UnifiedEnvelope.Status.ok,
                        List.of(new UnifiedEnvelope.Metric("actual_state", actualState, "status")),
                        Map.of("type", "command_feedback"))
        );

        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("status.actuators.topic", envelope);
        System.out.println("📢 [FEEDBACK] Sent status for " + actuatorId + ": " + actualState);
    }
}