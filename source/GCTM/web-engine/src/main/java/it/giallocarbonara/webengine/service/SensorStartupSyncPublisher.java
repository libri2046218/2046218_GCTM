package it.giallocarbonara.webengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SensorStartupSyncPublisher {

    private static final Logger logger = LoggerFactory.getLogger(SensorStartupSyncPublisher.class);

    private final JmsTemplate jmsTemplate;

    public SensorStartupSyncPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void requestSensorStatusesOnStartup() {
        Map<String, Object> syncRequest = Map.of(
                "action", "STATUS_SYNC",
                "origin", "web-engine",
                "reason", "startup"
        );

        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("command.sensors.topic", syncRequest);
        logger.info("Startup sync request published to command.sensors.topic");
    }
}
