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
12) As a Mission Control Operator, I want to export the last 24 hours of sensor data into a CSV or JSON file so that I can perform offline scientific analysis of the habitat's environment.
13) As a Mission Control Operator, I want to view a system-wide log of all automated actions taken by the rules so that I can audit why an actuator was triggered during the night.
14) As a Mission Control Operator, I want to define "Safe Range" thresholds for critical sensors (e.g., Oxygen)	so that the system can trigger a visual alarm even if no specific automation rule is set.
15) As a Mission Control Operator, I want to configure a data retention policy (e.g., delete logs older than 30 days), so that the system remains performant and doesn't run out of storage on the local Martian server.


# CONTAINERS:

## CONTAINER_NAME: Message-Broker

### DESCRIPTION: 
The Broker container is responsible for handling event communication between services.

### USER STORIES:
01 to 15 (All stories implicitly rely on the message broker for data transfer).

### PORTS: 
- "61616:61616"
- "5672:5672"
- "8161:8161"

### PERSISTENCE EVALUATION
The Broker container does not require data persistence to handle event communication.

### EXTERNAL SERVICES CONNECTIONS
The Broker container does not connect to external services.

### MICROSERVICES:

#### MICROSERVICE: activemq-broker
- TYPE: middleware
- DESCRIPTION: Implements the message broker to handle event communication between services.
- PORTS:
  - 61616
  - 5672
  - 8161
- TECHNOLOGICAL SPECIFICATION:
This microservice uses the latest image of Apache ActiveMQ as the message broker. As far as now, it is version 6.2.0.


## CONTAINER_NAME: Ingestion

### DESCRIPTION: 
The Ingestion container is responsible for collecting data from sensors communicating in different protocols and sending them to the message broker.

### USER STORIES:
01, 02, 03, 04, 05, 06, 07, 12, 14

### PERSISTENCE EVALUATION
The Ingestion container does not require data persistence to expose last values of the sensors.

### EXTERNAL SERVICES CONNECTIONS
The Ingestion container connect to the Mars Habitat Simulator to poll and consume raw data from the sensors.

### MICROSERVICES:

#### MICROSERVICE: sensors-ingestor
- TYPE: backend
- DESCRIPTION: Polls and consumes raw data from the simulator, adding metadata before pushing to the broker.
- TECHNOLOGICAL SPECIFICATION:
The sensors-ingestor container runs a Java Spring Boot application designed to handle standard web requests for its core logic and data transfer. It utilizes Jackson for JSON processing and is packaged as a standalone, executable artifact using the Spring Boot Maven plugin.
- SERVICE ARCHITECTURE: 
It utilizes a scheduled task mechanism to periodically make HTTP requests to the `mars-simulator` API, process the incoming telemetry, and publish it as `SensorData` payloads onto the ActiveMQ topics.


## CONTAINER_NAME: Actuation

### DESCRIPTION: 
The Actuation container is responsible for receiving commands from the message broker and executing actions on the physical actuators.

### USER STORIES:
05, 08, 10

### PERSISTENCE EVALUATION
The Actuation container does not require data persistence to receive and execute commands.

### EXTERNAL SERVICES CONNECTIONS
The Actuation container connects to the Mars Habitat Simulator to send commands to the actuators via HTTP requests.

### MICROSERVICES:

#### MICROSERVICE: actuator-manager
- TYPE: backend
- DESCRIPTION: Reads the status of the actuators and posts them to the message broker, listens for command messages from the broker and executes them against the actuators via HTTP requests.
- TECHNOLOGICAL SPECIFICATION:
The actuator-manager container runs a Java Spring Boot application. It utilizes Jackson for JSON processing and is packaged as a standalone, executable artifact using the Spring Boot Maven plugin.
- SERVICE ARCHITECTURE: 
It listens to the `command.actuators.topic` on the message broker. When an `ActuatorCommand` message is received, it evaluates the desired state and forwards the command via an HTTP REST call to the `mars-simulator`. It also synchronizes actuator statuses and broadcasts them back to the broker.


## CONTAINER_NAME: Automation

### DESCRIPTION: 
The Automation container is responsible for storing, evaluating and executing automations based on the data received from the message broker.

### USER STORIES:
05, 09, 10, 13

### PERSISTENCE EVALUATION
The Automation container requires data persistence to store the automations' rules.

### EXTERNAL SERVICES CONNECTIONS
The Automation container does not connect to external services.

### MICROSERVICES:

#### MICROSERVICE: automation-evaluator
- TYPE: backend
- DESCRIPTION: Evaluates automations based on the data received from the message broker and executes actions by sending events to the actuators' topics.
- TECHNOLOGICAL SPECIFICATION:
The microservice is built using Java Spring Boot, utilizing Spring Data JPA to interact with a PostgreSQL database, and Spring JMS to communicate with the ActiveMQ broker.
- SERVICE ARCHITECTURE: 
It acts as a consumer listening to telemetry data from the sensors. It queries the `db-automation` database for active rules, evaluates incoming metrics against predefined thresholds (e.g., using logical operators), and if the conditions are met, acts as a producer to publish `ActuatorCommand` messages to the actuator topics.

- DB STRUCTURE: 
	**_automation_rules_** :	| **_id_** | sensor_name | operator | value | actuator_name | actuator_state | manualOverride |

#### MICROSERVICE: db-automation
- TYPE: database
- DESCRIPTION: Manages the persistent storage and retrieval of automations' rules, including their conditions and actions.
- PORTS: 5433:5432
- TECHNOLOGICAL SPECIFICATION:
Utilizes a PostgreSQL 15 (Alpine) relational database image.
- SERVICE ARCHITECTURE: 
It provides a robust SQL backend mapped to a persistent volume (`postgres_data`) to ensure automation rules survive container restarts.


## CONTAINER_NAME: Logging

### DESCRIPTION: 
The Logging container is responsible for maintaining an audit trail of all automated actions and storing historical sensor data for offline export and analysis.

### USER STORIES:
12, 13, 14, 15

### PORTS: 
9200:9200 (Elasticsearch), 5601:5601 (Kibana - optional/disabled by default)

### PERSISTENCE EVALUATION
Requires data persistence to persistently store historical logs, system audit trails, and time-series sensor data.

### EXTERNAL SERVICES CONNECTIONS
Does not connect to external services.

### MICROSERVICES:

#### MICROSERVICE: history-service
- TYPE: backend
- DESCRIPTION: Listens to all broker traffic and persists it. Handles data pruning and file generation for export.
- PORTS: Internal communication only.
- TECHNOLOGICAL SPECIFICATION:
Java Spring Boot application utilizing Spring Data Elasticsearch to interact with the underlying document store, and Spring JMS to act as a system-wide eavesdropper on the message broker.
- SERVICE ARCHITECTURE: 
It implements several JMS listeners targeting various system topics (telemetry, commands, rules). Upon consuming a message, it parses the metadata and saves it as an `AuditLog` entity into Elasticsearch.

#### MICROSERVICE: db-history
- TYPE: database
- DESCRIPTION: Stores historical telemetry and audit trails.
- PORTS: 9200:9200
- TECHNOLOGICAL SPECIFICATION:
Utilizes an Elasticsearch 8.17 NoSQL document store optimized for time-series data and fast querying.
- SERVICE ARCHITECTURE: 
Exposes REST APIs on port 9200 for document insertion and search queries. Configured as a single-node cluster mapping data to a local volume (`logdata`).

- DB STRUCTURE: 
	**_system_logs_** (Index) :	| **_id_** | timestamp | type | correlationId | payload |


## CONTAINER_NAME: Presentation

### DESCRIPTION: 
The Presentation container serves the frontend application and acts as a backend-for-frontend (BFF) to bridge WebSocket communication with the message broker, allowing real-time monitoring.

### USER STORIES:
01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12, 13, 14, 15

### PORTS: 
8080:8080

### PERSISTENCE EVALUATION
The Presentation container does not require data persistence for showing sensors' data.

### EXTERNAL SERVICES CONNECTIONS
The Presentation container does not connect to external services.

### MICROSERVICES:

#### MICROSERVICE: web-engine
- TYPE: backend
- DESCRIPTION: Implements websocket communication to receive real-time data from the message broker and update the frontend accordingly.
- PORTS: 8080:8080
- TECHNOLOGICAL SPECIFICATION:
Java Spring Boot application with built-in Tomcat server and STOMP over WebSockets messaging configuration.
- SERVICE ARCHITECTURE: 
It serves the static frontend assets (`index.html`, `app.js`, `style.css`). It acts as a bridge: catching incoming STOMP messages from the browser UI and translating them to JMS topic messages on the ActiveMQ broker, while also subscribing to JMS topics to push updates down to connected browser clients.

- ENDPOINTS: 
		
	| STOMP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | PUB | /sensors/sync | Requests a full sync of sensor statuses | 01, 02, 03, 04, 06 |
    | PUB | /sensors/refresh | Forces a refresh for a specific sensor | 07 |
    | PUB | /actuators/sync | Requests a full sync of actuator statuses | 10 |
    | PUB | /actuators/control | Sends a command to change actuator state | 08 |
    | PUB | /rules/add | Submits a new automation rule | 05, 09 |

#### MICROSERVICE: dashboard-ui
- TYPE: frontend
- DESCRIPTION: Serves the web pages and renders the user interface for monitoring sensor data, controlling actuators, and managing automations.
- PORTS: Served on 8080
- TECHNOLOGICAL SPECIFICATION:
Built using standard web technologies: HTML5, CSS3, and JavaScript (`app.js`).
- SERVICE ARCHITECTURE: 
A Single Page Application (SPA) that initializes a STOMP WebSocket client to maintain a persistent bi-directional connection with the `web-engine`.

- PAGES: 

	| Name | Description | Related Microservice | User Stories |
	| ---- | ----------- | -------------------- | ------------ |
	| index.html | Main operator dashboard to visualize metrics, manage rules, and actuate controls in real-time. | web-engine | 01-15 |