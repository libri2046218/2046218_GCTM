package it.giallocarbonara.sensorsingestor.producer;

import it.giallocarbonara.AutomRule;
import it.giallocarbonara.Header;
import it.giallocarbonara.Metric;
import it.giallocarbonara.SensorData;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Component
@EnableScheduling
public class FakeSensorsProducer {

    private final JmsTemplate jmsTemplate;
    private final Random random = new Random();

    // Track generated rules for automatic deletion: ruleId -> (AutomRule, creationTime)
    private final Map<UUID, RuleContext> generatedRules = new HashMap<>();
    private static final long DELETION_DELAY_MS = 10000L; // Delete after 10 seconds

    private final List<SensorConfig> sensorConfigs = List.of(
            new SensorConfig("greenhouse_temperature", "temperature", "°C", 15.0, 35.0),
            new SensorConfig("entrance_humidity", "humidity", "%", 30.0, 70.0),
            new SensorConfig("co2_hall", "co2", "ppm", 300.0, 1000.0),
            new SensorConfig("hydroponic_ph", "ph", "pH", 5.5, 7.5),
            new SensorConfig("mars/telemetry/solar_array", "power_output", "kW", 0.0, 50.0),
            new SensorConfig("mars/telemetry/radiation", "radiation_level", "mSv", 0.01, 5.0)
    );

    // Add these lists at the class level to provide random choices for the rules
    private final List<String> operators = List.of(">", "<", ">=", "<=", "==");
    private final List<String> actuatorNames = List.of("HVAC_SYSTEM", "WATER_PUMP", "ALARM_SIREN", "WINDOW_BLINDS", "MAIN_VALVE");
    private final List<String> actuatorStates = List.of("ON", "OFF", "OPEN", "CLOSED");
    private final List<String> ruleSensorNames = List.of("greenhouse_temperature", "entrance_humidity", "co2_hall", "hydroponic_ph");

    public FakeSensorsProducer(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @Scheduled(fixedRate = 5000)
    public void fakeNewRule() {
        System.out.println("🛠️ [SENDER] Initializing test automation rule...");

        // Generate random rule parameters
        String sensorName = ruleSensorNames.get(random.nextInt(ruleSensorNames.size()));
        String operator = operators.get(random.nextInt(operators.size()));
        String actuatorName = actuatorNames.get(random.nextInt(actuatorNames.size()));
        String actuatorState = actuatorStates.get(random.nextInt(actuatorStates.size()));
        boolean manualOverride = Boolean.FALSE;

        // Generate a random threshold value between 0.0 and 100.0, rounded to 2 decimal places
        double value = random.nextDouble() * 100.0;
        double roundedValue = Math.round(value * 100.0) / 100.0;

        // Build the Rule DTO
        AutomRule rule = new AutomRule(
                new Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        "fake-rules-generator",
                        null,
                        null),
                sensorName,
                operator,
                roundedValue,
                actuatorName,
                actuatorState,
                manualOverride,
                false
        );

        // Send to the Topic for rules ingestion
        jmsTemplate.convertAndSend("newrules.topic", rule);

        // Track this rule for automatic deletion
        UUID ruleId = rule.header().msg_id();
        generatedRules.put(ruleId, new RuleContext(rule, System.currentTimeMillis()));

        // Log the generated rule in a readable format
        System.out.printf("🛠️ [SENDER] Rule-ID: %s | Created Rule: IF %s %s %.2f THEN SET %s TO %s (Override: %b) [Will delete in %dms]%n",
                ruleId,
                sensorName,
                operator,
                roundedValue,
                actuatorName,
                actuatorState,
                manualOverride,
                DELETION_DELAY_MS);
    }

    @Scheduled(fixedRate = 2000)
    public void checkAndDeleteExpiredRules() {
        long now = System.currentTimeMillis();
        
        // Collect expired rule IDs first to avoid ConcurrentModificationException
        List<UUID> expiredRuleIds = new java.util.ArrayList<>();
        
        for (var entry : generatedRules.entrySet()) {
            if ((now - entry.getValue().createdAt) > DELETION_DELAY_MS) {
                expiredRuleIds.add(entry.getKey());
            }
        }
        
        // Now process the expired rules
        for (UUID ruleId : expiredRuleIds) {
            RuleContext context = generatedRules.get(ruleId);
            if (context != null) {
                AutomRule originalRule = context.rule;

                // Create a deletion request with the same parameters
                AutomRule deleteRequest = new AutomRule(
                    new Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        "fake-rules-generator-delete",
                        null,
                        null
                    ),
                    originalRule.sensorName(),
                    originalRule.operator(),
                    originalRule.value(),
                    originalRule.actuatorName(),
                    originalRule.actuatorState(),
                    originalRule.manualOverride(),
                    true
                );

                // Send deletion request
                jmsTemplate.convertAndSend("newrules.topic", deleteRequest);
                
                System.out.printf("🗑️ [SENDER] Deletion Request-ID: %s | Deleting Rule-ID: %s | " +
                        "IF %s %s %.2f THEN SET %s TO %s%n",
                        deleteRequest.header().msg_id(),
                        ruleId,
                        originalRule.sensorName(),
                        originalRule.operator(),
                        originalRule.value(),
                        originalRule.actuatorName(),
                        originalRule.actuatorState()
                );

                generatedRules.remove(ruleId);
            }
        }
    }

    @Scheduled(fixedRate = 1000)
    public void fakeData() {
        System.out.println("🚀 [SENDER] Initializing test message...");

        // Randomly select a sensor profile
        SensorConfig config = sensorConfigs.get(random.nextInt(sensorConfigs.size()));
        double value = config.min + (config.max - config.min) * random.nextDouble();
        double roundedValue = Math.round(value * 100.0) / 100.0;

        SensorData sensorData = new SensorData(
                new Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        "fake-sensors-ingestor",
                        null,
                        null),
                config.subjectId,
                "ok",
                List.of(new Metric(config.metricName, roundedValue, config.unit))
                );

        // Send to the Topic for normalization testing [cite: 73]
        jmsTemplate.convertAndSend("sensors.topic", sensorData);

        System.out.printf("🚀 [SENDER] Message-ID: %s |Sent %s metric from %s: %.2f %s%n",
                sensorData.header().msg_id(), config.metricName, config.subjectId, roundedValue, config.unit);
    }

    private record SensorConfig(String subjectId, String metricName, String unit, double min, double max) {}

    private record RuleContext(AutomRule rule, long createdAt) {}

}

