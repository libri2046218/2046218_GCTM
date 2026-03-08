# SYSTEM DESCRIPTION:

..............

# USER STORIES:

..............

# EVENT SCHEMA:

```json
{
  "type": "object",
  "required": ["header", "payload"],
  "properties": {
    "header": {
      "type": "object",
      "required": ["msg_id", "timestamp", "msg_type", "origin"],
      "properties": {
        "msg_id": { "type": "string", "format": "uuid" },
        "timestamp": { "type": "string", "format": "date-time" },
        "msg_type": { 
          "type": "string", 
          "enum": ["TELEMETRY", "RPC_REQUEST", "RPC_RESPONSE", "SYSTEM_ALERT"] 
        },
        "origin": { "type": "string" },
        "correlation_id": { "type": "string", "description": "Obbligatorio per RPC" },
        "reply_to": { "type": "string", "description": "Coda di risposta per RPC_REQUEST" }
      }
    },
    "payload": {
      "type": "object",
      "required": ["subject_id"],
      "properties": {
        "subject_id": { "type": "string", "description": "ID del sensore o dell'attuatore" },
        "status": { "type": "string", "enum": ["ok", "warning", "error", "SUCCESS", "FAILED"] },
        "metrics": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["name", "value"],
            "properties": {
              "name": { "type": "string" },
              "value": { "type": ["number", "string", "boolean"] },
              "unit": { "type": "string" }
            }
          }
        },
        "metadata": { "type": "object", "description": "Dati extra come subsystem, loop, segment" }
      }
    }
  }
}
```
# RULE MODEL:

...............