package it.giallocarbonara.automationevaluator.producer;

import it.giallocarbonara.UnifiedEnvelope;
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
        UnifiedEnvelope commandEnvelope = new UnifiedEnvelope(
                new UnifiedEnvelope.Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        UnifiedEnvelope.MsgType.RPC_REQUEST,
                        "automation-evaluator",
                        null, // correlation_id opzionale qui
                        null
                ),
                new UnifiedEnvelope.Payload(
                        targetId,
                        UnifiedEnvelope.Status.ok,
                        List.of(new UnifiedEnvelope.Metric("action", action, "command")),
                        Map.of("priority", "high")
                )
        );

        jmsTemplate.convertAndSend("actuators.commands", commandEnvelope);
        System.out.println("📤 Comando '" + action + "' inviato a actuators.commands");
    }
}