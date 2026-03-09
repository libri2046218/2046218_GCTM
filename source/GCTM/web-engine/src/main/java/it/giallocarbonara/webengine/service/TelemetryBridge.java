package it.giallocarbonara.webengine.service;
import it.giallocarbonara.UnifiedEnvelope;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class TelemetryBridge {

    private final SimpMessagingTemplate messagingTemplate;

    public TelemetryBridge(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @JmsListener(destination = "sensors.topic")
    public void onSensorMessage(UnifiedEnvelope message) {
        messagingTemplate.convertAndSend("/topic/sensors", message);
    }

    @JmsListener(destination = "actuators.topic")
    public void onActuatorMessage(UnifiedEnvelope message) {
        messagingTemplate.convertAndSend("/topic/actuators", message);

        // Route RPC responses to a dedicated feedback channel
        if (message.header().msg_type() == UnifiedEnvelope.MsgType.RPC_RESPONSE) {
            messagingTemplate.convertAndSend("/topic/actuators/response", message);
        }
    }
}