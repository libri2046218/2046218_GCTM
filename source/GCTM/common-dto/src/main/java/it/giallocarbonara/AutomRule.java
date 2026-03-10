package it.giallocarbonara;

public record AutomRule(
        Header header,
        String sensorName,
        String operator,
        Double value,
        String actuatorName,
        String actuatorState,
        Boolean manualOverride,
        Boolean deletionReq
) {}
