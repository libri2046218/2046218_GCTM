package it.giallocarbonara.sensorsingestor;

import it.giallocarbonara.AutomRule;
import it.giallocarbonara.Header;
import it.giallocarbonara.Metric;
import it.giallocarbonara.SensorData;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
@EnableScheduling
public class FakeSensorsProducer {

    private final JmsTemplate jmsTemplate;
    private final Random random = new Random();

    private final List<SensorConfig> sensorConfigs = List.of(
            new SensorConfig("greenhouse_temperature", "temperature", "°C", 15.0, 35.0),
            new SensorConfig("entrance_humidity", "humidity", "%", 30.0, 70.0),
            new SensorConfig("co2_hall", "co2", "ppm", 300.0, 1000.0),
            new SensorConfig("hydroponic_ph", "ph", "pH", 5.5, 7.5),
            new SensorConfig("mars/telemetry/solar_array", "power_output", "kW", 0.0, 50.0),
            new SensorConfig("mars/telemetry/radiation", "radiation_level", "mSv", 0.01, 5.0)
    );

    public FakeSensorsProducer(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
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


}