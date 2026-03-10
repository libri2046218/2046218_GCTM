package it.giallocarbonara;

import java.util.List;

public record SensorData(
        Header header,
        String sensor_id,
        String status,
        List<Metric> metrics
) {}
