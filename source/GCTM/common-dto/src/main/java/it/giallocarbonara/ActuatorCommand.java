package it.giallocarbonara;

public record ActuatorCommand(
        Header header,
        String actuator_id,
        String desired_state
) {}