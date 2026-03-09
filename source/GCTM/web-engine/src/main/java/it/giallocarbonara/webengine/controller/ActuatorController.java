package it.giallocarbonara.webengine.controller;

import it.giallocarbonara.UnifiedEnvelope;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class ActuatorController {

    private final JmsTemplate jmsTemplate;

    public ActuatorController(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @MessageMapping("/actuators/control")
    public void handleActuatorCommand(Map<String, String> command) {
        String actuatorId = command.get("actuatorId");
        String action = command.get("action");

        UnifiedEnvelope request = new UnifiedEnvelope(
                new UnifiedEnvelope.Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        UnifiedEnvelope.MsgType.RPC_REQUEST,
                        "web-engine",
                        null,
                        null
                ),
                new UnifiedEnvelope.Payload(
                        actuatorId,
                        UnifiedEnvelope.Status.ok,
                        List.of(new UnifiedEnvelope.Metric("state", action, "bool")),
                        null
                )
        );

        jmsTemplate.convertAndSend("actuators.topic", request);
    }
}
