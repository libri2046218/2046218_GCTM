package it.giallocarbonara.automationevaluator.consumer;

import it.giallocarbonara.UnifiedEnvelope;
import it.giallocarbonara.automationevaluator.service.AutomationService;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class NewRulesListener {
    private final AutomationService automationService;

    public NewRulesListener(AutomationService automationService) {
        this.automationService = automationService;
    }

    @JmsListener(destination = "newrules.topic")
    public void onMessage(UnifiedEnvelope envelope) {
        // Log di monitoraggio
        envelope.printSummary();

        // Passiamo l'intero oggetto al servizio per la valutazione
        automationService.createNewRule(envelope);
    }
}

