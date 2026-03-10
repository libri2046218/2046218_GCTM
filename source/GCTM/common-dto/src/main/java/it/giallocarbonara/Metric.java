package it.giallocarbonara;

// Record per la singola Metrica
public record Metric(
        String name,
        Object value, // Object per supportare number, string, boolean
        String unit
) {}