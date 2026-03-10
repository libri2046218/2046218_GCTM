package it.giallocarbonara.automationevaluator.consumer;

import it.giallocarbonara.RefreshRequest;
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
    public void onMessage(Object message) {
        if (!(message instanceof RefreshRequest request)) {
            System.out.println("[RuleRequestListener] Ignored non-refresh payload on rulerequest.topic: "
                    + (message == null ? "null" : message.getClass().getName()));
            return;
        }

        // Log di monitoraggio
        System.out.println("Received rule request: " + request);

        // Passiamo la richiesta al servizio per ottenere le regole
        automationService.fetchRules();
    }
}


