package it.giallocarbonara.automationevaluator.consumer;

import it.giallocarbonara.AutomRule;
import it.giallocarbonara.RefreshRequest;
import it.giallocarbonara.UnifiedEnvelope;
import it.giallocarbonara.automationevaluator.service.AutomationService;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;



@Component
public class RuleRequestListener {
    private final AutomationService automationService;

    public RuleRequestListener(AutomationService automationService) {
        this.automationService = automationService;
    }

    @JmsListener(destination = "rulerequest.topic")
    public void onMessage(RefreshRequest request) {
        // Log di monitoraggio
        System.out.println("Received rule request: " + request);

        // Passiamo la richiesta al servizio per ottenere le regole
        automationService.fetchRules();
    }
}


