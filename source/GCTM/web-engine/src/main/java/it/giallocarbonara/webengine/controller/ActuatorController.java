package it.giallocarbonara.webengine.controller;

import it.giallocarbonara.ActuatorCommand;
import it.giallocarbonara.AutomRule;
import it.giallocarbonara.Header;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Controller
public class ActuatorController {

    private static final Logger logger = LoggerFactory.getLogger(ActuatorController.class);
    private final JmsTemplate jmsTemplate;

    public ActuatorController(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @MessageMapping("/actuators/sync")
    public void requestActuatorsSync() {
        ActuatorCommand syncRequest = new ActuatorCommand(
                new Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        "web-engine",
                        UUID.randomUUID().toString(),
                        "/topic/actuators/status"
                ),
                "*",
                "STATUS_SYNC"
        );

        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("command.actuators.topic", syncRequest);
        logger.info("Actuator status sync request published to command.actuators.topic");
    }

    /**
     * Handles WebSocket commands from the frontend and publishes ActuatorCommand to the broker.
     * Frontend sends: {actuatorId: "...", action: "ON"/"OFF"}
     */
    @MessageMapping("/actuators/control")
    public void handleActuatorCommand(Map<String, String> command) {
        String actuatorId = command.get("actuatorId");
        String action = command.get("action");

        logger.info("WebSocket command received: actuator={}, action={}", actuatorId, action);

        // Create ActuatorCommand message
        ActuatorCommand commandMessage = new ActuatorCommand(
                new Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        "web-engine",
                        null,                          // correlation_id (optional)
                        "/topic/actuators/status"      // reply_to: where status updates will be sent
                ),
                actuatorId,
                action  // desired_state ("ON" or "OFF")
        );

        // Send to actuator-manager via broker topic
        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("command.actuators.topic", commandMessage);
        logger.debug("ActuatorCommand published to command.actuators.topic: actuator={}, state={}", actuatorId, action);
    }

    /**
     * Handles automation rule creation from the frontend and publishes to automation-evaluator.
     * Frontend sends: {sensor, operator, threshold, actuator, action}
     */
    @MessageMapping("/rules/add")
    public void handleRuleCreation(Map<String, Object> ruleData) {
        String sensor = (String) ruleData.get("sensor");
        String operator = (String) ruleData.get("operator");
        Double threshold = null;
        if (ruleData.get("threshold") instanceof Number) {
            threshold = ((Number) ruleData.get("threshold")).doubleValue();
        }
        String actuator = (String) ruleData.get("actuator");
        String action = (String) ruleData.get("action");

        logger.info("WebSocket rule creation received: sensor={}, operator={}, threshold={}, actuator={}, action={}",
                sensor, operator, threshold, actuator, action);

        // Create AutomRule message
        AutomRule automRule = new AutomRule(
                new Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        "web-engine",
                        null,
                        null
                ),
                sensor,
                operator,
                threshold,
                actuator,
                action,
                false  // manualOverride = false (rule is enabled by default)
        );

        // Send to automation-evaluator via broker topic
        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("newrules.topic", automRule);
        logger.info("AutomRule published to newrules.topic: sensor={}, actuator={}", sensor, actuator);
    }
}
