package it.giallocarbonara.automationevaluator.consumer;

import it.giallocarbonara.UnifiedEnvelope;
import it.giallocarbonara.automationevaluator.service.AutomationService;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class SensorsListener {

    private final AutomationService automationService;

    public SensorsListener(AutomationService automationService) {
        this.automationService = automationService;
    }

    @JmsListener(destination = "sensors.topic")
    public void onMessage(UnifiedEnvelope envelope) {
        // Log di monitoraggio
        envelope.printSummary();

        // Passiamo l'intero oggetto al servizio per la valutazione
        automationService.evaluate(envelope);
    }
}