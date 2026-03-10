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
- TYPE: backend
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
<list of user stories satisfied>

### PORTS: 
<used ports>

### DESCRIPTION:
<description of the container>

### PERSISTENCE EVALUATION
The Ingestion container does not require data persistence to expose last values of the sensors.

### EXTERNAL SERVICES CONNECTIONS
The Ingestion container does not connect to external services.

### MICROSERVICES:

#### MICROSERVICE: telemetry-ingestor
- TYPE: backend
- DESCRIPTION: Polls and consumes raw data from the simulator, adding metadata before pushing to the broker.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |

## CONTAINER_NAME: Actuation

### DESCRIPTION: 
The Actuation container is responsible for receiving commands from the message broker and executing actions on the physical actuators.

### USER STORIES:
<list of user stories satisfied>

### PORTS: 
<used ports>

### DESCRIPTION:
<description of the container>

### PERSISTENCE EVALUATION
The Actuation container does not require data persistence to receive and execute commands.

### EXTERNAL SERVICES CONNECTIONS
The Actuation container does not connect to external services.

### MICROSERVICES:

#### MICROSERVICE: actuator-manager
- TYPE: backend
- DESCRIPTION: Reads the status of the actuators and posts them to the message broker, listens for command messages from the broker and executes them against the actuators via HTTP requests.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |


## CONTAINER_NAME: Automation

### DESCRIPTION: 
The Automation container is responsible for storing, evaluating and executing automations based on the data received from the message broker.

### USER STORIES:
<list of user stories satisfied>

### PORTS: 
<used ports>

### DESCRIPTION:
<description of the container>

### PERSISTENCE EVALUATION
The Automation container requires data persistence to store the automations' rules.

### EXTERNAL SERVICES CONNECTIONS
The Automation container does not connect to external services.

### MICROSERVICES:

#### MICROSERVICE: automation-evaluator
- TYPE: backend
- DESCRIPTION: Evaluates automations based on the data received from the message broker and executes actions by sending events to the actuators' topics.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |


- DB STRUCTURE: 

	**_<name of the table>_** :	| **_id_** | <other columns>

#### MICROSERVICE: db-automation
- TYPE: database
- DESCRIPTION: Manages the persistent storage and retrieval of automations' rules, including their conditions and actions.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |

## CONTAINER_NAME: Logging

### DESCRIPTION: 
<description of the container>

### USER STORIES:
<list of user stories satisfied>

### PORTS: 
<used ports>

### DESCRIPTION:
<description of the container>

### PERSISTENCE EVALUATION
<description on the persistence of data>

### EXTERNAL SERVICES CONNECTIONS
<description on the connections to external services>

### MICROSERVICES:

#### MICROSERVICE: history-service
- TYPE: backend
- DESCRIPTION: Listens to all broker traffic and persists it. Handles data pruning and file generation for export.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |

#### MICROSERVICE: db-history
- TYPE: database
- DESCRIPTION: Stores historical telemetry and audit trails.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |

- PAGES: <put this bullet point only in the case of frontend and fill the following table>

	| Name | Description | Related Microservice | User Stories |
	| ---- | ----------- | -------------------- | ------------ |
	| ... | ... | ... | ... |

- DB STRUCTURE: <put this bullet point only in the case a DB is used in the microservice and specify the structure of the tables and columns>

	**_<name of the table>_** :	| **_id_** | <other columns>

## CONTAINER_NAME: Presentation

### DESCRIPTION: 
The Presentation container is responsible for providing a user interface to interact with the system, allowing users to monitor sensor data, control actuators, and manage automations.

### USER STORIES:
<list of user stories satisfied>

### PORTS: 
<used ports>

### DESCRIPTION:
<description of the container>

### PERSISTENCE EVALUATION
The Presentation container does not require data persistence for showing sensors' data.

### EXTERNAL SERVICES CONNECTIONS
The Presentation container does not connect to external services.

### MICROSERVICES:

#### MICROSERVICE: web-engine
- TYPE: backend
- DESCRIPTION: Implements websocket communication to receive real-time data from the message broker and update the frontend accordingly.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |

#### MICROSERVICE: dashboard-ui
- TYPE: frontend
- DESCRIPTION: Serves the web pages and renders the user interface for monitoring sensor data, controlling actuators, and managing automations.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- PAGES: <put this bullet point only in the case of frontend and fill the following table>

	| Name | Description | Related Microservice | User Stories |
	| ---- | ----------- | -------------------- | ------------ |
	| ... | ... | ... | ... |
