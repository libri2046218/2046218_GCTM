package it.giallocarbonara.automationevaluator.consumer;

import it.giallocarbonara.AutomRule;
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
    public void onMessage(AutomRule rule) {
        // Log di monitoraggio
        System.out.println("[NewRulesListener] Received rule: " + rule.header().msg_id());

        if (Boolean.TRUE.equals(rule.deletionReq())) {
            System.out.println("[NewRulesListener] Processing deletion request for rule: " + rule.sensorName());
            automationService.deleteRule(rule);
            return;
        }

        // Passiamo l'intero oggetto al servizio per la valutazione
        System.out.println("[NewRulesListener] Creating new rule: " + rule.sensorName());
        automationService.createNewRule(rule);
    }
}

