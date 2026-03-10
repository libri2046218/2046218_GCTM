package it.giallocarbonara.webengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;

/**
 * Web Engine Application - BFF and Real-time Communication Hub
 * 
 * Responsibilities:
 * - WebSocket server for real-time dashboard updates (/web-engine)
 * - RPC gateway for actuator commands (UI → JMS → actuator-manager → JMS → UI)
 * - Message bridge between ActiveMQ broker and WebSocket clients
 * - Frontend for Mars Base Ground Control Telemetry System
 * 
 * Configuration:
 * - JMS enabled for ActiveMQ connectivity
 * - WebSocket STOMP endpoints for client communication
 * - Scans it.giallocarbonara package for shared configuration
 */
@SpringBootApplication(scanBasePackages = "it.giallocarbonara")
@EnableJms
public class WebEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebEngineApplication.class, args);
    }

}

