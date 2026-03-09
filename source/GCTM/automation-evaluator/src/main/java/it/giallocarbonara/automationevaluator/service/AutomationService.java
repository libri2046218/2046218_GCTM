package it.giallocarbonara.automationevaluator.service;

import it.giallocarbonara.UnifiedEnvelope;
import it.giallocarbonara.automationevaluator.producer.CommandProducer;
import org.springframework.stereotype.Service;

@Service
public class AutomationService {

    private final CommandProducer commandProducer;

    public AutomationService(CommandProducer commandProducer) {
        this.commandProducer = commandProducer;
    }

    public void evaluate(UnifiedEnvelope envelope) {
        // Analizziamo ogni metrica nel payload
        for (UnifiedEnvelope.Metric metric : envelope.payload().metrics()) {

            // Esempio: Controllo Pressione
            if ("battery".equalsIgnoreCase(metric.name())) {
                double val = ((Number) metric.value()).doubleValue();
                if (val < 90.0) {
                    triggerEmergencyAction(envelope.payload().subject_id(), "CHARGE_BATTERY");
                }
            }

        }
    }

    private void triggerEmergencyAction(String subjectId, String action) {
        System.out.println("⚠️ CONDIZIONE CRITICA su " + subjectId + ": " + action);
        commandProducer.sendCommand(subjectId, action);
    }
}