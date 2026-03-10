package it.giallocarbonara.automationevaluator.producer;

import it.giallocarbonara.ActuatorCommand;
import it.giallocarbonara.Header;
import it.giallocarbonara.UnifiedEnvelope;
import it.giallocarbonara.automationevaluator.entity.AutomationRule;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
}