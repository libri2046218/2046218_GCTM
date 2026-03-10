package it.giallocarbonara.webengine.service;

import it.giallocarbonara.ActuatorStatus;
import it.giallocarbonara.SensorData;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TelemetryBridge bridges messages from ActiveMQ broker to WebSocket clients.
 * 
 * Message Flow:
 * - sensors.topic (JMS) → /topic/sensors (WebSocket) for real-time sensor data
 * - status.actuators.topic (JMS) → /topic/actuators/status (WebSocket) for actuator status updates
 */
@Service
public class TelemetryBridge {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryBridge.class);
    private final SimpMessagingTemplate messagingTemplate;

    public TelemetryBridge(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Listens to sensor data from the broker and forwards to WebSocket clients.
     * Message type: SensorData from sensors-ingestor service.
     */
    @JmsListener(destination = "sensors.topic", subscription = "web-engine-sensors")
    public void onSensorMessage(SensorData message) {
        logger.debug("Received sensor message: sensor={}, status={}", 
                    message.sensor_id(), message.status());
        
        try {
            messagingTemplate.convertAndSend("/topic/sensors", message);
            logger.trace("Sensor message routed to /topic/sensors");
        } catch (Exception e) {
            logger.error("Error forwarding sensor message to WebSocket", e);
        }
    }

    /**
     * Listens to actuator status updates from the broker and forwards to WebSocket clients.
     * Message type: ActuatorStatus from actuator-manager service.
     */
    @JmsListener(destination = "status.actuators.topic", subscription = "web-engine-actuators-status")
    public void onActuatorStatusMessage(ActuatorStatus message) {
        logger.debug("Received actuator status: actuator={}, state={}", 
                    message.actuator_id(), message.actual_state());
        
        try {
            // Broadcast actuator status to WebSocket clients
            messagingTemplate.convertAndSend("/topic/actuators/status", message);
            logger.trace("Actuator status routed to /topic/actuators/status");
        } catch (Exception e) {
            logger.error("Error forwarding actuator status to WebSocket", e);
        }
    }
}