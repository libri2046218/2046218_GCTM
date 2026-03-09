package it.giallocarbonara.sensorsingestor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.giallocarbonara.UnifiedEnvelope;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
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

    private final List<String> sensorEndpoints = List.of(
            "greenhouse_temperature", "entrance_humidity", "co2_hall",
            "hydroponic_ph", "water_tank_level", "corridor_pressure",
            "air_quality_pm25", "air_quality_voc"
    );

    public SensorsIngestorApplication(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(SensorsIngestorApplication.class, args);
    }

    @Bean
    public CommandLineRunner connectToWebSockets() {
        return args -> {
            StandardWebSocketClient client = new StandardWebSocketClient();
            List<String> topics = List.of(
                    "mars/telemetry/solar_array", "mars/telemetry/radiation",
                    "mars/telemetry/life_support", "mars/telemetry/thermal_loop",
                    "mars/telemetry/power_bus", "mars/telemetry/power_consumption",
                    "mars/telemetry/airlock"
            );

            for (String topic : topics) {
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

    @Scheduled(fixedRate = 5000)
    public void fetchAndSendSensorData() {
        for (String endpoint : sensorEndpoints) {
            try {
                String url = "http://mars-simulator:8080/api/sensors/" + endpoint;
                String jsonRaw = restTemplate.getForObject(url, String.class);
                JsonNode root = mapper.readTree(jsonRaw);
                if (root != null) processAndSend(root, endpoint);
            } catch (Exception e) {
                System.err.println("❌ [REST ERROR] " + endpoint + ": " + e.getMessage());
            }
        }
    }

    private void processAndSend(JsonNode node, String sourceId) {
        String subjectId = sourceId; // Default
        if (node.has("sensor_id")) subjectId = node.get("sensor_id").asText();
        else if (node.has("topic")) subjectId = node.get("topic").asText();
        else if (node.has("airlock_id")) subjectId = node.get("airlock_id").asText();

        List<UnifiedEnvelope.Metric> metrics = new ArrayList<>();

        // 1. SCHEMI SCALARI / AMBIENTALI (rest.scalar.v1 & topic.environment.v1)
        if (node.has("value") && node.has("metric")) {
            metrics.add(new UnifiedEnvelope.Metric(node.get("metric").asText(), node.get("value").asDouble(), node.get("unit").asText("")));
        }
        // 2. SCHEMI CHIMICI / LISTE (rest.chemistry.v1 & topic.environment.v1)
        else if (node.has("measurements")) {
            node.get("measurements").forEach(m -> metrics.add(new UnifiedEnvelope.Metric(
                    m.get("metric").asText(), m.get("value").asDouble(), m.get("unit").asText(""))));
        }
        // 3. SCHEMA POWER (topic.power.v1)
        else if (node.has("power_kw")) {
            metrics.add(new UnifiedEnvelope.Metric("power", node.get("power_kw").asDouble(), "kW"));
            metrics.add(new UnifiedEnvelope.Metric("voltage", node.get("voltage_v").asDouble(), "V"));
            metrics.add(new UnifiedEnvelope.Metric("current", node.get("current_a").asDouble(), "A"));
            metrics.add(new UnifiedEnvelope.Metric("cumulative", node.get("cumulative_kwh").asDouble(), "kWh"));
        }
        // 4. SCHEMA PARTICOLATO (rest.particulate.v1)
        else if (node.has("pm25_ug_m3")) {
            metrics.add(new UnifiedEnvelope.Metric("pm1", node.get("pm1_ug_m3").asDouble(), "ug/m3"));
            metrics.add(new UnifiedEnvelope.Metric("pm25", node.get("pm25_ug_m3").asDouble(), "ug/m3"));
            metrics.add(new UnifiedEnvelope.Metric("pm10", node.get("pm10_ug_m3").asDouble(), "ug/m3"));
        }
        // 5. SCHEMA LIVELLO (rest.level.v1)
        else if (node.has("level_pct")) {
            metrics.add(new UnifiedEnvelope.Metric("level_pct", node.get("level_pct").asDouble(), "%"));
            metrics.add(new UnifiedEnvelope.Metric("level_liters", node.get("level_liters").asDouble(), "L"));
        }
        // 6. SCHEMA THERMAL (topic.thermal_loop.v1)
        else if (node.has("temperature_c") && node.has("loop")) {
            metrics.add(new UnifiedEnvelope.Metric("temperature", node.get("temperature_c").asDouble(), "°C"));
            metrics.add(new UnifiedEnvelope.Metric("flow", node.get("flow_l_min").asDouble(), "L/min"));
        }
        // 7. SCHEMA AIRLOCK (topic.airlock.v1)
        else if (node.has("cycles_per_hour")) {
            metrics.add(new UnifiedEnvelope.Metric("cycles", node.get("cycles_per_hour").asDouble(), "c/h"));
            metrics.add(new UnifiedEnvelope.Metric("state", node.get("last_state").asText(), "status"));
        }

        if (!metrics.isEmpty()) {
            UnifiedEnvelope.Status envStatus = (node.path("status").asText("ok").equals("warning")) ?
                    UnifiedEnvelope.Status.warning : UnifiedEnvelope.Status.ok;

            UnifiedEnvelope envelope = new UnifiedEnvelope(
                    new UnifiedEnvelope.Header(UUID.randomUUID(), Instant.now(), UnifiedEnvelope.MsgType.TELEMETRY, "sensors-ingestor", null, null),
                    new UnifiedEnvelope.Payload(subjectId, envStatus, metrics, null)
            );

            jmsTemplate.setPubSubDomain(true);
            jmsTemplate.convertAndSend("sensors.topic", envelope);
            System.out.println("🚀 [INGESTOR] Dispatched normalized data for: " + subjectId);
        }
    }
}