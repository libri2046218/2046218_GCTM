package it.giallocarbonara.historyservice.consumer;

import it.giallocarbonara.*;
import it.giallocarbonara.historyservice.entity.AuditLog;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

@Service
public class AuditLogger {
    private final ElasticsearchOperations esOps;

    public AuditLogger(ElasticsearchOperations esOps) {
        this.esOps = esOps;
    }

    // Selector filters by the "_type" header property
    @JmsListener(destination = "sensors.topic", selector = "_type = 'SensorData'")
    public void onSensorData(SensorData payload) {
        saveToElastic(payload, payload.header());
    }

    @JmsListener(destination = "sensors.topic", selector = "_type = 'ActuatorCommand'")
    public void onActuatorCommand(ActuatorCommand payload) {
        saveToElastic(payload, payload.header());
    }

    @JmsListener(destination = "sensors.topic", selector = "_type = 'ActuatorStatus'")
    public void onActuatorStatus(ActuatorStatus payload) {
        saveToElastic(payload, payload.header());
    }

    @JmsListener(destination = "sensors.topic", selector = "_type = 'AutomRule'")
    public void onAutomRule(AutomRule payload) {
        saveToElastic(payload, payload.header());
    }

    private void saveToElastic(Object payload, Header header) {
        AuditLog logEntry = new AuditLog(
                header.msg_id().toString(),
                header.timestamp(),
                payload.getClass().getSimpleName(),
                header.correlation_id(),
                payload
        );

        System.out.println(("LOGGER: Writing in ElasticSearch"));
        esOps.save(logEntry);
    }
}