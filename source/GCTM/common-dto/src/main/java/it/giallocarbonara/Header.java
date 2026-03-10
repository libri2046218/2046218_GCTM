package it.giallocarbonara;

import java.time.Instant;
import java.util.UUID;

public record Header(
        UUID msg_id,
        Instant timestamp,
        String sender,
        String correlation_id, // Opzionale per Telemetria, Obbligatorio per RPC
        String reply_to        // Opzionale
) {}