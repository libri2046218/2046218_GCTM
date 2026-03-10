package it.giallocarbonara;

public record AutomRule(
        Header header,
        Long id,
        String sensorName,
        String metricName,
        String operator,
        Double value,
        String valueText,
        String actuatorName,
        String actuatorState,
        Boolean manualOverride,
        Boolean deletionReq
) {}
