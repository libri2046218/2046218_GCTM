package it.giallocarbonara.sensorsingestor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.giallocarbonara.Header;
import it.giallocarbonara.Metric;
import it.giallocarbonara.SensorData;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
@EnableScheduling
public class SensorsIngestorApplication {

    private final JmsTemplate jmsTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    // Endpoint di base per i sensori
    private final String SENSORS_BASE_URL = "http://mars-simulator:8080/api/sensors";

    public SensorsIngestorApplication(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(SensorsIngestorApplication.class, args);
    }

    /**
     * AZIONE RICHIESTA: Chiamata REST GET api/sensors generica all'avvio.
     * Recupera l'elenco di tutti i sensori disponibili e ne pubblica lo stato iniziale.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void broadcastInitialSensorStatus() {
        try {
            System.out.println("🔍 [STARTUP] Recupero elenco sensori da " + SENSORS_BASE_URL);

            // 1. Chiamata GET generica per ottenere la lista dei sensori
            JsonNode sensorList = restTemplate.getForObject(SENSORS_BASE_URL, JsonNode.class);

            if (sensorList != null && sensorList.isArray()) {
                System.out.println("📡 [STARTUP] Trovati " + sensorList.size() + " sensori. Inizio broadcast...");

                // 2. Iterazione sulla lista restituita dal simulatore
                for (JsonNode s : sensorList) {
                    String sensorName = s.asText();
                    fetchAndProcessSingleSensor(sensorName);
                }
                System.out.println("✅ [STARTUP] Broadcast iniziale completato.");
            }
        } catch (Exception e) {
            System.err.println("❌ [STARTUP ERROR] Impossibile recuperare lista sensori: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 5000)
    public void scheduledFetch() {
        // Possiamo riutilizzare la logica del broadcast iniziale o usare una lista statica
        // Per efficienza, qui usiamo la lista statica definita precedentemente
        List<String> staticEndpoints = List.of(
                "greenhouse_temperature", "entrance_humidity", "co2_hall",
                "hydroponic_ph", "water_tank_level", "corridor_pressure",
                "air_quality_pm25", "air_quality_voc"
        );
        staticEndpoints.forEach(this::fetchAndProcessSingleSensor);
    }

    /**
     * Helper per recuperare i dati di un singolo sensore e inviarli al broker.
     */
    private void fetchAndProcessSingleSensor(String endpoint) {
        try {
            String url = SENSORS_BASE_URL + "/" + endpoint;
            String jsonRaw = restTemplate.getForObject(url, String.class);
            if (jsonRaw != null) {
                JsonNode root = mapper.readTree(jsonRaw);
                processAndSend(root, endpoint);
            }
        } catch (Exception e) {
            System.err.println("❌ [FETCH ERROR] " + endpoint + ": " + e.getMessage());
        }
    }

    private void processAndSend(JsonNode node, String sourceId) {
        // Identificazione del soggetto
        String subjectId = sourceId;
        if (node.has("sensor_id")) subjectId = node.get("sensor_id").asText();

        List<Metric> metrics = new ArrayList<>();

        // Logica di mapping (Metrica Scalare)
        if (node.has("value") && node.has("metric")) {
            metrics.add(new Metric(node.get("metric").asText(), node.get("value").asDouble(), node.get("unit").asText("")));
        }
        // Logica di mapping (Misure Multiple/Chimica)
        else if (node.has("measurements")) {
            node.get("measurements").forEach(m -> metrics.add(new Metric(
                    m.get("metric").asText(), m.get("value").asDouble(), m.get("unit").asText(""))));
        }
        // ... (altri schemi di mapping PM25, Level, ecc. come nel codice precedente)

        if (!metrics.isEmpty()) {
            SensorData sensorData = new SensorData(
                    new Header(UUID.randomUUID(), Instant.now(), "sensors-ingestor", null, null),
                    subjectId,
                    node.path("status").asText("ok"),
                    metrics
            );

            jmsTemplate.setPubSubDomain(true);
            jmsTemplate.convertAndSend("sensors.topic", sensorData);
        }
    }

    @Bean
    public CommandLineRunner connectToWebSockets() {
        return args -> {
            // Logica WebSocket (invariata)
            StandardWebSocketClient client = new StandardWebSocketClient();
            String wsUrl = "ws://mars-simulator:8080/api/telemetry/ws?topic=mars/telemetry/solar_array";
            // ... resto dell'implementazione WS
        };
    }
}