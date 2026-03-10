package it.giallocarbonara.historyservice.Entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.time.Instant;

@Document(indexName = "system-logs")
public record LogEntry(
        @Id String id,
        String type,          // SENSOR_DATA, ACTUATOR_CMD, etc.
        Instant timestamp,
        String sender,
        String correlationId,
        Object payload        // The original DTO
) {}