package it.giallocarbonara.automationevaluator.producer;

import it.giallocarbonara.ActuatorCommand;
import it.giallocarbonara.AutomRule;
import it.giallocarbonara.Header;
import it.giallocarbonara.automationevaluator.entity.AutomationRule;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class CommandProducer {

    private final JmsTemplate jmsTemplate;

    public CommandProducer(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void sendCommand(String targetId, String action) {
        // Creiamo l'inviluppo di risposta/comando

        ActuatorCommand actuatorCommand = new ActuatorCommand(
                new Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        "automation-evaluator",
                        null,
                        null
                ),
                targetId,
                action
                );

        jmsTemplate.convertAndSend("command.actuators.topic", actuatorCommand);
        System.out.println("📤 Comando '" + action + "' inviato a command.actuators.topic");
    }

    public void sendRule(AutomationRule rule) {
        AutomRule ruleSnapshot = new AutomRule(
            new Header(
                UUID.randomUUID(),
                Instant.now(),
                "automation-evaluator",
                null,
                "/topic/rules"
            ),
            rule.getId(),
            rule.getSensorName(),
            rule.getMetricName(),
            rule.getOperator(),
            rule.getValue(),
            rule.getValueText(),
            rule.getActuatorName(),
            rule.getActuatorState(),
            rule.getManualOverride(),
            false
        );

        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("ruleresponse.topic", ruleSnapshot);
    }
}