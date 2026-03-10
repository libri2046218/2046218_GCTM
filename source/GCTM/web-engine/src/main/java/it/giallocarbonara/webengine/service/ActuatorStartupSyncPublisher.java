package it.giallocarbonara.webengine.service;

import it.giallocarbonara.ActuatorCommand;
import it.giallocarbonara.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ActuatorStartupSyncPublisher {

    private static final Logger logger = LoggerFactory.getLogger(ActuatorStartupSyncPublisher.class);

    private final JmsTemplate jmsTemplate;

    public ActuatorStartupSyncPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void requestActuatorStatusesOnStartup() {
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
        logger.info("Startup sync request published to command.actuators.topic");
    }
}
