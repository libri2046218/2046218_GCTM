# SYSTEM DESCRIPTION:

<system of the system>

# USER STORIES:

<list of user stories>


# CONTAINERS:

## CONTAINER_NAME: Broker

### DESCRIPTION: 
The Broker container is responsible for handling event communication between backend and frontend services.

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

#### MICROSERVICE: broker-service
- TYPE: backend
- DESCRIPTION: Implements the message broker to handle event communication between backend and frontend services.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |


## CONTAINER_NAME: Sensors

### DESCRIPTION: 
The Sensors container is responsible for collecting data from sensors communicating in different protocols and sending them to the message broker.

### USER STORIES:
<list of user stories satisfied>

### PORTS: 
<used ports>

### DESCRIPTION:
<description of the container>

### PERSISTENCE EVALUATION
The Sensors container does not require data persistence to expose last values of the sensors.

### EXTERNAL SERVICES CONNECTIONS
The Sensors container does not connect to external services.

### MICROSERVICES:

#### MICROSERVICE: rest-sensors
- TYPE: backend
- DESCRIPTION: Collects last values from REST sensors and posts them to the message broker.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |

#### MICROSERVICE: telemetry-streamer
- TYPE: backend
- DESCRIPTION: Collects data from telemetry stream and posts them to the message broker.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |

## CONTAINER_NAME: Actuators

### DESCRIPTION: 
The Actuators container is responsible for receiving commands from the message broker and executing actions on the physical actuators.

### USER STORIES:
<list of user stories satisfied>

### PORTS: 
<used ports>

### DESCRIPTION:
<description of the container>

### PERSISTENCE EVALUATION
The Actuators container does not require data persistence to receive and execute commands.

### EXTERNAL SERVICES CONNECTIONS
The Actuators container does not connect to external services.

### MICROSERVICES:

#### MICROSERVICE: actuator-reader
- TYPE: backend
- DESCRIPTION: Reads the status of the actuators and posts them to the message broker.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |

#### MICROSERVICE: actuator-writer
- TYPE: backend
- DESCRIPTION: Receives commands from the message broker and executes actions on the physical actuators.
- PORTS: <ports to be published by the microservice>
- TECHNOLOGICAL SPECIFICATION:
<description of the technological aspect of the microservice>
- SERVICE ARCHITECTURE: 
<description of the architecture of the microservice>

- ENDPOINTS: <put this bullet point only in the case of backend and fill the following table>
		
	| HTTP METHOD | URL | Description | User Stories |
	| ----------- | --- | ----------- | ------------ |
    | ... | ... | ... | ... |

## CONTAINER_NAME: Automations

### DESCRIPTION: 
The Automations container is responsible for storing, evaluating and executing automations based on the data received from the message broker.

### USER STORIES:
<list of user stories satisfied>

### PORTS: 
<used ports>

### DESCRIPTION:
<description of the container>

### PERSISTENCE EVALUATION
The Automations container requires data persistence to store the automations' rules.

### EXTERNAL SERVICES CONNECTIONS
The Automations container does not connect to external services.

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

#### MICROSERVICE: mariadb-automations
- TYPE: database
- DESCRIPTION: Manages the persistent storage and retrieval of automations' rules, including their conditions and actions.
- PORTS: ...

## CONTAINER_NAME: Web-Interface

### DESCRIPTION: 
The Web-Interface container is responsible for providing a user interface to interact with the system, allowing users to monitor sensor data, control actuators, and manage automations.

### USER STORIES:
<list of user stories satisfied>

### PORTS: 
<used ports>

### DESCRIPTION:
<description of the container>

### PERSISTENCE EVALUATION
The Web-Interface container does not require data persistence for showing sensors' data.

### EXTERNAL SERVICES CONNECTIONS
The Web-Interface container does not connect to external services.

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

#### MICROSERVICE: page-renderer
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
