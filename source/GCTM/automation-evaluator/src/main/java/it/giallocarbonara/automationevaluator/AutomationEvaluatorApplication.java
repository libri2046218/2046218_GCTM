package it.giallocarbonara.automationevaluator;

import it.giallocarbonara.UnifiedEnvelope;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.JmsListener;

@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
public class AutomationEvaluatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomationEvaluatorApplication.class, args);
    }

    /**
     * Listener per la telemetria dei SENSORI.
     */
    @JmsListener(destination = "sensors.topic")
    public void onSensorMessage(UnifiedEnvelope message) {
        System.out.println("🌡️ [SENSOR RECEIVER] Received from: " + message.header().origin());
        message.payload().metrics().forEach(m ->
                System.out.println("   -> " + m.name() + ": " + m.value() + " " + m.unit())
        );
    }

    /**
     * Listener per lo stato degli ATTUATORI (inviato all'avvio o dopo un comando).
     */
    @JmsListener(destination = "status.actuators.topic")
    public void onActuatorMessage(UnifiedEnvelope message) {
        System.out.println("⚙️ [ACTUATOR RECEIVER] Status Update from: " + message.header().origin());
        System.out.println("   Actuator ID: " + message.payload().subject_id());

        // Estrazione sicura dello stato dalle metriche
        if (!message.payload().metrics().isEmpty()) {
            var stateMetric = message.payload().metrics().get(0);
            System.out.println("   Current State: " + stateMetric.value());
        }

        // Verifica se è un messaggio di startup
        if (message.payload().metadata() != null && "startup_sync".equals(message.payload().metadata().get("event"))) {
            System.out.println("   ℹ️ This is a startup synchronization message.");
        }
    }
}
