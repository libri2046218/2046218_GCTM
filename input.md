# SYSTEM DESCRIPTION:

..............

# USER STORIES:

..............

# EVENT SCHEMA:

```json
{
  "type": "object",
  "required": ["event_id", "timestamp", "source", "event_type", "payload"],
  "properties": {
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "Standardized ISO-8601 observation time, mapped from captured_at, event_time, or updated_at."
    },
    "source": {
      "type": "string",
      "description": "The origin of the event (e.g., 'sensor:greenhouse_temperature', 'topic:mars/telemetry/solar_array', 'actuator:pump_1')."
    },
    "event_type": {
      "type": "string",
      "description": "The original contract type (e.g., 'rest.scalar.v1', 'topic.power.v1', 'actuator.response')."
    },
    "status": {
      "type": "string",
      "enum": ["ok", "warning", "error", "unknown"],
      "description": "System or sensor health status. Default to 'ok' if not provided by the origin."
    },
    "payload": {
      "type": "object",
      "required": ["measurements"],
      "properties": {
        "tags": {
          "type": "object",
          "additionalProperties": { "type": "string" },
          "description": "Contextual metadata (e.g., subsystem, loop, segment, airlock_id)."
        },
        "measurements": {
          "type": "array",
          "description": "Flattened array of metric-value-unit objects for time-series ingestion.",
          "items": {
            "type": "object",
            "required": ["metric", "value"],
            "properties": {
              "metric": { "type": "string" },
              "value": { "type": ["number", "string"] },
              "unit": { "type": "string" }
            }
          }
        }
      }
    }
  }
}
```

# RULE MODEL:

...............