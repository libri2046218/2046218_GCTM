package it.giallocarbonara;

import java.time.Instant;

public record ActuatorStatus(
        Header header,
        String actuator_id,
        String actual_state,
        Instant updated_at

) {
}
