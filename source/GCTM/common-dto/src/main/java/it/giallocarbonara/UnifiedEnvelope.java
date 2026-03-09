package it.giallocarbonara;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UnifiedEnvelope(
        Header header,
        Payload payload
) {
    // Record per l'Header
    public record Header(
            UUID msg_id,
            OffsetDateTime timestamp,
            MsgType msg_type,
            String origin,
            String correlation_id, // Opzionale per Telemetria, Obbligatorio per RPC
            String reply_to        // Opzionale
    ) {}

    // Record per il Payload
    public record Payload(
            String subject_id,
            Status status,
            List<Metric> metrics,
            Map<String, Object> metadata // Per gestire dati extra dinamici
    ) {}

    // Record per la singola Metrica
    public record Metric(
            String name,
            Object value, // Object per supportare number, string, boolean
            String unit
    ) {}

    // Enums per garantire la consistenza dei dati
    public enum MsgType {
        TELEMETRY, RPC_REQUEST, RPC_RESPONSE, SYSTEM_ALERT
    }

    public enum Status {
        ok, warning, error, SUCCESS, FAILED
    }
}

/* esempio di utilizzo:

UnifiedEnvelope envelope = new UnifiedEnvelope(
    new UnifiedEnvelope.Header(UUID.randomUUID(), OffsetDateTime.now(), MsgType.TELEMETRY, "sensors-ingestor", null, null),
    new UnifiedEnvelope.Payload("temp-sensor-01", Status.ok,
        List.of(new UnifiedEnvelope.Metric("temperature", 22.5, "Celsius")),
        Map.of("segment", "A1"))
);
 */