# SYSTEM DESCRIPTION:

<system of the system>

# USER STORIES:

<list of user stories>


# CONTAINERS:

## CONTAINER_NAME: Message-Broker

### DESCRIPTION: 
The Broker container is responsible for handling event communication between services.

### USER STORIES:
<list of user stories satisfied>

### PORTS: 
<used ports>

### DESCRIPTION:
<description of the container>

### PERSISTENCE EVALUATION
The Broker container does not require data persistence to handle event communication.

### EXTERNAL SERVICES CONNECTIONS
The Broker container does not connect to external services.

### MICROSERVICES:

#### MICROSERVICE: activemq-broker
- TYPE: backend
- DESCRIPTION: Implements the message broker to handle event communication between services.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |


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
