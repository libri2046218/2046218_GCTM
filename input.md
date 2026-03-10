# SYSTEM DESCRIPTION:

GialloCarbonara To Mars is a platform that allows people stationed on Mars to monitor their habitat's conditions and control its actuators, ensuring their safety and comfort. The system will alow scientists and operators to define automations and analyse sensors log data to optimize the habitat's environment and resource usage.

# USER STORIES:

01) As a Base Habitat Operator, I want to see the current greenhouse temperature on the dashboard so that I can ensure the Martian crops do not freeze
02) As a Base Habitat Operator, I want to view a line chart of the power consumption from the power bus so that I can identify unusual energy spikes in real-time
03) As a Base Habitat Operator, I want to see the water tank level displayed in both percentage and liters so that I can manage our limited liquid reserves accurately
04) As a Base Habitat Operator, I want the dashboard to highlight sensors with a "warning" status (like the CO2 hall sensor) so that I can prioritize repairs on malfunctioning life support hardware
05) As a Base Habitat Operator, I want to define a rule that automatically sets the hall ventilation to ON if the air quality VOC measurements exceed safe levels
06) As a Technical Habitat Operator, I want to see all sensor data grouped by type (power related, air quality, ...)
07) As a Technical Habitat Operator, I want to see when the last reading was received for each sensor and force a refresh if needed
08) As a Technical Habitat Operator, I want a way to control actuators manually
09) As a Technical Habitat Operator, I want suggestions to write rules (like names of sensors or actuators) and that I can verify if the rule is valid
10) As a Technical Habitat Operator, I want for each actuator a view of its status and related automations
11) As a Mission Control Operator, I want to visualize the network health status (latency/connectivity) of the message broker and sensors so that I can ensure the communication backbone is stable.
12) As a Mission Control Operator, I want to export sensor data into a CSV or JSON file so that I can perform offline scientific analysis of the habitat's environment.
13) As a Mission Control Operator, I want to view a system-wide log of all automated actions taken by the rules so that I can audit why an actuator was triggered.
14) As a Mission Control Operator, I want to define "Safe Range" thresholds for critical sensors (e.g., Oxygen)	so that the system can trigger a visual alarm even if no specific automation rule is set.
15) As a Mission Control Operator, I want to see when an actuator changes state, whatever page I am on.

# EVENT SCHEMA:

```json
{
  "title": "GCTM Event and Rule Schemas",
  "description": "JSON Schemas for the GialloCarbonara To Mars IoT Message Broker Events",
  "definitions": {
    "Header": {
      "type": "object",
      "properties": {
        "msg_id": {
          "type": "string",
          "format": "uuid",
          "description": "Unique identifier for the message"
        },
        "timestamp": {
          "type": "string",
          "format": "date-time",
          "description": "ISO-8601 timestamp of when the event was generated"
        },
        "sender": {
          "type": "string",
          "description": "Identifier of the microservice that generated the event"
        },
        "correlation_id": {
          "type": ["string", "null"],
          "description": "Optional ID used to trace RPC requests/responses"
        },
        "reply_to": {
          "type": ["string", "null"],
          "description": "Optional topic name where the response should be routed"
        }
      },
      "required": ["msg_id", "timestamp", "sender"]
    },
    "Metric": {
      "type": "object",
      "properties": {
        "name": {
          "type": "string"
        },
        "value": {
          "type": ["number", "string", "boolean"],
          "description": "Value of the metric. Can be numeric, string, or boolean."
        },
        "unit": {
          "type": "string",
          "description": "Unit of measurement (e.g., Celsius, Percentage)"
        }
      },
      "required": ["name", "value", "unit"]
    },
    "SensorData": {
      "type": "object",
      "description": "This is the unified event schema for all sensor data events. It includes a header for metadata and a payload that contains the sensor ID, status, and an array of metrics.",
      "properties": {
        "header": { "$ref": "#/definitions/Header" },
        "sensor_id": { "type": "string" },
        "status": { "type": "string" },
        "metrics": {
          "type": "array",
          "items": { "$ref": "#/definitions/Metric" }
        }
      },
      "required": ["header", "sensor_id", "status", "metrics"]
    },
    "ActuatorCommand": {
      "type": "object",
      "description": "This is the event schema for all actuator commands. ",
      "properties": {
        "header": { "$ref": "#/definitions/Header" },
        "actuator_id": { "type": "string" },
        "desired_state": { "type": "string" }
      },
      "required": ["header", "actuator_id", "desired_state"]
    },
    "ActuatorStatus": {
      "type": "object",
      "description": "This is the event schema for all actuator status updates. ",
      "properties": {
        "header": { "$ref": "#/definitions/Header" },
        "actuator_id": { "type": "string" },
        "actual_state": { "type": "string" },
        "updated_at": {
          "type": "string",
          "format": "date-time"
        }
      },
      "required": ["header", "actuator_id", "actual_state", "updated_at"]
    }
  }
}
```
# RULE MODEL:

The rules are modeled as JSON objects that define the conditions under which certain actions should be taken based on sensor data according to the following schema:

```json
"AutomRule": {
  "type": "object",
  "properties": {
    "header": { "$ref": "#/definitions/Header" },
    "sensorName": { "type": "string" },
    "metricName": {"type": "string"},
    "operator": { "type": "string", "description": "Logical operator, e.g., >, <, ==, >=, <=" },
    "value": { "type": "number", "description": "Threshold value to trigger the rule" },
    "actuatorName": { "type": "string" },
    "actuatorState": { "type": "string", "description": "Target state to apply if the condition is met" },
    "manualOverride": { "type": "boolean", "description": "If true, the rule is disabled/bypassed" },
    "deletionReq": { "type": "boolean", "description": "If true, this rule should be deleted from the system" }
  },
  "required": ["header", "sensorName", "operator", "value", "actuatorName", "actuatorState", "manualOverride"]
},
```