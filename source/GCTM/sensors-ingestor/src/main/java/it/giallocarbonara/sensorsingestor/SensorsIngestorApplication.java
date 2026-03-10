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
import org.springframework.jms.annotation.JmsListener;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
@EnableScheduling
public class SensorsIngestorApplication {

    private static final String SENSORS_BASE_URL = "http://mars-simulator:8080/api/sensors";

    private static final List<String> STATIC_SENSOR_ENDPOINTS = List.of(
        "greenhouse_temperature", "entrance_humidity", "co2_hall",
        "hydroponic_ph", "water_tank_level", "corridor_pressure",
        "air_quality_pm25", "air_quality_voc"
    );

    private static final List<String> TELEMETRY_TOPICS = List.of(
        "mars/telemetry/solar_array", "mars/telemetry/radiation",
        "mars/telemetry/life_support", "mars/telemetry/thermal_loop",
        "mars/telemetry/power_bus", "mars/telemetry/power_consumption",
        "mars/telemetry/airlock"
    );

    private final JmsTemplate jmsTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, SensorData> lastKnownState = new ConcurrentHashMap<>();

    public SensorsIngestorApplication(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(SensorsIngestorApplication.class, args);
    }

    @JmsListener(destination = "command.sensors.topic", subscription = "sensors-ingestor-sync")
    public void onSensorsSyncRequest(Map<String, Object> command) {
        String action = String.valueOf(command.getOrDefault("action", ""));

        if ("STATUS_SYNC".equalsIgnoreCase(action)) {
            System.out.println("[SYNC] Received STATUS_SYNC request. Broadcasting sensor snapshot...");
            broadcastInitialSensorStatus();

        } else if ("FORCE_REFRESH".equalsIgnoreCase(action)) {
            String sensorId = String.valueOf(command.getOrDefault("sensorId", ""));
            System.out.println("[REFRESH] Force refresh for: " + sensorId);

            if (STATIC_SENSOR_ENDPOINTS.contains(sensorId)) {
                // REST sensor: re-fetch fresh data from simulator
                fetchAndProcessSingleSensor(sensorId);
            } else {
                // WS/Telemetry sensor: republish last known state from cache
                SensorData cached = lastKnownState.get(sensorId);
                if (cached != null) {
                    jmsTemplate.setPubSubDomain(true);
                    jmsTemplate.convertAndSend("sensors.topic", cached);
                    System.out.println("[REFRESH] Republished cached state for: " + sensorId);
                } else {
                    System.err.println("[REFRESH] No cached state found for: " + sensorId);
                }
            }
        }
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
            String jsonRaw = restTemplate.getForObject(SENSORS_BASE_URL, String.class);
            if (jsonRaw == null || jsonRaw.isBlank()) {
                System.err.println("⚠️ [STARTUP] Risposta vuota da " + SENSORS_BASE_URL);
                return;
            }

            JsonNode responseNode = mapper.readTree(jsonRaw);
            JsonNode sensorList = responseNode;
            if (responseNode != null && responseNode.isObject() && responseNode.has("sensors")) {
                sensorList = responseNode.get("sensors");
            }

            if (sensorList != null && sensorList.isArray()) {
                System.out.println("📡 [STARTUP] Trovati " + sensorList.size() + " sensori. Inizio broadcast...");

                // 2. Iterazione sulla lista restituita dal simulatore
                for (JsonNode s : sensorList) {
                    String sensorName = s.asText();
                    fetchAndProcessSingleSensor(sensorName);
                }
                System.out.println("✅ [STARTUP] Broadcast iniziale completato.");
            } else {
                System.err.println("⚠️ [STARTUP] Formato inatteso lista sensori: " + responseNode);
            }
        } catch (Exception e) {
            System.err.println("❌ [STARTUP ERROR] Impossibile recuperare lista sensori: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Scheduled(fixedRate = 5000)
    public void scheduledFetch() {
        STATIC_SENSOR_ENDPOINTS.forEach(this::fetchAndProcessSingleSensor);
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

    @Bean
    public CommandLineRunner connectToWebSockets() {
        return args -> {
            StandardWebSocketClient client = new StandardWebSocketClient();
            for (String topic : TELEMETRY_TOPICS) {
                String wsUrl = "ws://mars-simulator:8080/api/telemetry/ws?topic=" + topic;
                client.execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                        JsonNode root = mapper.readTree(message.getPayload());
                        processAndSend(root, topic);
                    }
                    @Override
                    public void afterConnectionEstablished(WebSocketSession session) {
                        System.out.println("✅ [WS] Subscribed to: " + topic);
                    }
                }, wsUrl);
            }
        };
    }

    private void processAndSend(JsonNode node, String sourceId) {
        String subjectId = sourceId; // Default
        if (node.has("sensor_id")) subjectId = node.get("sensor_id").asText();
        else if (node.has("topic")) subjectId = node.get("topic").asText();
        else if (node.has("airlock_id")) subjectId = node.get("airlock_id").asText();

        List<Metric> metrics = new ArrayList<>();

        // 1. SCHEMI SCALARI / AMBIENTALI (rest.scalar.v1 & topic.environment.v1)
        if (node.has("value") && node.has("metric")) {
            metrics.add(new Metric(node.get("metric").asText(), node.get("value").asDouble(), node.get("unit").asText("")));
        }
        // 2. SCHEMI CHIMICI / LISTE (rest.chemistry.v1 & topic.environment.v1)
        else if (node.has("measurements")) {
            node.get("measurements").forEach(m -> metrics.add(new Metric(
                    m.get("metric").asText(), m.get("value").asDouble(), m.get("unit").asText(""))));
        }
        // 3. SCHEMA POWER (topic.power.v1)
        else if (node.has("power_kw")) {
            metrics.add(new Metric("power", node.get("power_kw").asDouble(), "kW"));
            metrics.add(new Metric("voltage", node.get("voltage_v").asDouble(), "V"));
            metrics.add(new Metric("current", node.get("current_a").asDouble(), "A"));
            metrics.add(new Metric("cumulative", node.get("cumulative_kwh").asDouble(), "kWh"));
        }
        // 4. SCHEMA PARTICOLATO (rest.particulate.v1)
        else if (node.has("pm25_ug_m3")) {
            metrics.add(new Metric("pm1", node.get("pm1_ug_m3").asDouble(), "ug/m3"));
            metrics.add(new Metric("pm25", node.get("pm25_ug_m3").asDouble(), "ug/m3"));
            metrics.add(new Metric("pm10", node.get("pm10_ug_m3").asDouble(), "ug/m3"));
        }
        // 5. SCHEMA LIVELLO (rest.level.v1)
        else if (node.has("level_pct")) {
            metrics.add(new Metric("level_pct", node.get("level_pct").asDouble(), "%"));
            metrics.add(new Metric("level_liters", node.get("level_liters").asDouble(), "L"));
        }
        // 6. SCHEMA THERMAL (topic.thermal_loop.v1)
        else if (node.has("temperature_c") && node.has("loop")) {
            metrics.add(new Metric("temperature", node.get("temperature_c").asDouble(), "°C"));
            metrics.add(new Metric("flow", node.get("flow_l_min").asDouble(), "L/min"));
        }
        // 7. SCHEMA AIRLOCK (topic.airlock.v1)
        else if (node.has("cycles_per_hour")) {
            metrics.add(new Metric("cycles", node.get("cycles_per_hour").asDouble(), "c/h"));
            metrics.add(new Metric("state", node.get("last_state").asText(), "status"));
        }

        if (!metrics.isEmpty()) {
            String envStatus = node.path("status").asText("ok");

            SensorData sensorData = new SensorData(
                    new Header(
                            UUID.randomUUID(),
                            Instant.now(),
                            "sensors-ingestor",
                            null,
                            null
                    ),
                    subjectId,
                    envStatus,
                    metrics
            );

            lastKnownState.put(subjectId, sensorData);
            jmsTemplate.setPubSubDomain(true);
            jmsTemplate.convertAndSend("sensors.topic", sensorData);
            System.out.println("🚀 [INGESTOR] Dispatched normalized data for: " + subjectId);
        }
    }
}