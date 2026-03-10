package it.giallocarbonara.historyservice.Consumer;

import it.giallocarbonara.ActuatorCommand;
import it.giallocarbonara.Header;
import it.giallocarbonara.SensorData;
import it.giallocarbonara.historyservice.Entity.LogEntry;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

@Service
public class LogIngestionService {

    private final ElasticsearchOperations elasticsearchOperations;

    public LogIngestionService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @JmsListener(destination = "sensors.topic")
    public void consumeSensorData(SensorData data) {
        saveLog("SENSOR_DATA", data.header(), data);
    }

    @JmsListener(destination = "actuators.topic")
    public void consumeCommand(ActuatorCommand cmd) {
        saveLog("ACTUATOR_COMMAND", cmd.header(), cmd);
    }

    private void saveLog(String type, Header header, Object fullBody) {
        LogEntry entry = new LogEntry(
                header.msg_id().toString(),
                type,
                header.timestamp(),
                header.sender(),
                header.correlation_id(),
                fullBody
        );

        System.out.println("LOGGING: " + header.msg_id());

        elasticsearchOperations.save(entry);
    }
}