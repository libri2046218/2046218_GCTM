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
        System.out.println(rule.header().msg_id());

        // Passiamo l'intero oggetto al servizio per la valutazione
        automationService.createNewRule(rule);
    }
}

