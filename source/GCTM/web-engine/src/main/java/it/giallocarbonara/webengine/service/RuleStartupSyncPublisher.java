package it.giallocarbonara.webengine.service;

import it.giallocarbonara.Header;
import it.giallocarbonara.RefreshRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RuleStartupSyncPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RuleStartupSyncPublisher.class);

    private final JmsTemplate jmsTemplate;

    public RuleStartupSyncPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void requestRulesOnStartup() {
        RefreshRequest refreshRequest = new RefreshRequest(
                new Header(
                        UUID.randomUUID(),
                        Instant.now(),
                        "web-engine",
                        UUID.randomUUID().toString(),
                        "/topic/rules"
                )
        );

        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.convertAndSend("rulerequest.topic", refreshRequest);
        logger.info("Rule snapshot sync request published to rulerequest.topic");
        logger.info("Startup sync request published to rulerequest.topic");
    }
}
