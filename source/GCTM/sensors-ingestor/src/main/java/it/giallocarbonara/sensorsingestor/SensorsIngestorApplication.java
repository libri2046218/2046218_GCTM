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
                fetchAndProcessSingleSensor(sensorId);
            } else {
                SensorData cached = lastKnownState.get(sensorId);
                if (cached != null) {
                    jmsTemplate.setPubSubDomain(true);
                    jmsTemplate.convertAndSend("sensors.topic", cached);
                    System.out.println("[REFRESH] Republished cached state for: " + sensorId);
                }
            }
        }
    }

    /**
     * Esegue la GET generica /api/sensors all'avvio e per ogni sensore trovato
     * effettua il broadcast dello stato iniziale.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void broadcastInitialSensorStatus() {
        try {
            System.out.println("🔍 [STARTUP] Recupero elenco sensori da " + SENSORS_BASE_URL);
            String jsonRaw = restTemplate.getForObject(SENSORS_BASE_URL, String.class);

            if (jsonRaw != null && !jsonRaw.isBlank()) {
                JsonNode responseNode = mapper.readTree(jsonRaw);
                // Gestione flessibile sia se array diretto che se oggetto con chiave "sensors"
                JsonNode sensorList = responseNode.isArray() ? responseNode : responseNode.get("sensors");

                if (sensorList != null && sensorList.isArray()) {
                    System.out.println("📡 [STARTUP] Trovati " + sensorList.size() + " sensori. Inizio broadcast...");
                    for (JsonNode s : sensorList) {
                        fetchAndProcessSingleSensor(s.asText());
                    }
                    System.out.println("✅ [STARTUP] Broadcast iniziale completato.");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ [STARTUP ERROR] Impossibile recuperare lista sensori: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 5000)
    public void scheduledFetch() {
        STATIC_SENSOR_ENDPOINTS.forEach(this::fetchAndProcessSingleSensor);
    }

    private void fetchAndProcessSingleSensor(String sensorId) {
        try {
            String url = SENSORS_BASE_URL + "/" + sensorId;
            String jsonRaw = restTemplate.getForObject(url, String.class);
            if (jsonRaw != null) {
                processAndSend(mapper.readTree(jsonRaw), sensorId);
            }
        } catch (Exception e) {
            System.err.println("❌ [REST ERROR] " + sensorId + ": " + e.getMessage());
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
                        processAndSend(mapper.readTree(message.getPayload()), topic);
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
        // --- LOGICA DI SPLIT ---
        // Pulisce l'ID (es: "mars/telemetry/solar_array" -> "solar_array")
        String[] parts = sourceId.split("/");
        String cleanSubjectId = parts[parts.length - 1];

        // Se il JSON contiene un ID interno più specifico, usiamo quello (sempre splittato)
        if (node.has("sensor_id")) {
            String[] sParts = node.get("sensor_id").asText().split("/");
            cleanSubjectId = sParts[sParts.length - 1];
        }

        List<Metric> metrics = new ArrayList<>();

        // Logica di mapping (Scalari, Chimici, Power, ecc.)
        if (node.has("value") && node.has("metric")) {
            // rest.scalar.v1: greenhouse_temperature, entrance_humidity, co2_hall, corridor_pressure
            metrics.add(new Metric(node.get("metric").asText(), node.get("value").asDouble(), node.get("unit").asText("")));
        } else if (node.has("measurements")) {
            // rest.chemistry.v1 & topic.environment.v1: hydroponic_ph, air_quality_voc, radiation, life_support
            node.get("measurements").forEach(m -> metrics.add(new Metric(
                    m.get("metric").asText(), m.get("value").asDouble(), m.get("unit").asText(""))));
        } else if (node.has("power_kw")) {
            // topic.power.v1: solar_array, power_bus, power_consumption
            metrics.add(new Metric("power", node.get("power_kw").asDouble(), "kW"));
            metrics.add(new Metric("voltage", node.get("voltage_v").asDouble(), "V"));
            metrics.add(new Metric("current", node.get("current_a").asDouble(), "A"));
            metrics.add(new Metric("cumulative_kwh", node.get("cumulative_kwh").asDouble(), "kWh"));
        } else if (node.has("pm25_ug_m3")) {
            // rest.particulate.v1: air_quality_pm25
            metrics.add(new Metric("pm1", node.get("pm1_ug_m3").asDouble(), "ug/m3"));
            metrics.add(new Metric("pm25", node.get("pm25_ug_m3").asDouble(), "ug/m3"));
            metrics.add(new Metric("pm10", node.get("pm10_ug_m3").asDouble(), "ug/m3"));
        } else if (node.has("level_pct")) {
            // rest.level.v1: water_tank_level
            metrics.add(new Metric("level_pct", node.get("level_pct").asDouble(), "%"));
            metrics.add(new Metric("level_liters", node.get("level_liters").asDouble(), "L"));
        } else if (node.has("temperature_c") && node.has("loop")) {
            // topic.thermal_loop.v1: thermal_loop
            metrics.add(new Metric("temperature", node.get("temperature_c").asDouble(), "°C"));
            metrics.add(new Metric("flow", node.get("flow_l_min").asDouble(), "L/min"));
        } else if (node.has("cycles_per_hour")) {
            // topic.airlock.v1: airlock
            metrics.add(new Metric("cycles_per_hour", node.get("cycles_per_hour").asDouble(), "cph"));
            metrics.add(new Metric("state", node.get("last_state").asText(), "status"));
        }

        if (!metrics.isEmpty()) {
            SensorData sensorData = new SensorData(
                    new Header(UUID.randomUUID(), Instant.now(), "sensors-ingestor", null, null),
                    cleanSubjectId,
                    node.path("status").asText("ok"),
                    metrics
            );

            lastKnownState.put(cleanSubjectId, sensorData);
            jmsTemplate.setPubSubDomain(true);
            jmsTemplate.convertAndSend("sensors.topic", sensorData);
            System.out.println("🚀 [INGESTOR] Dispatched: " + cleanSubjectId);
        }
    }
}
