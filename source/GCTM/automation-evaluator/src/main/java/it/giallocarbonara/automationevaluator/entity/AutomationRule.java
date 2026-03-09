package it.giallocarbonara.automationevaluator.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "automation_rules")
public class AutomationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_name")
    private String sensorName;

    @Column(name = "operator")
    private String operator; // es. "<", ">"

    @Column(name = "value")
    private Double value;

    @Column(name = "actuator_name")
    private String actuatorName;

    public enum ActuatorState {ON, OFF};
    @Enumerated(EnumType.STRING)
    @Column(name = "actuator_state")
    private ActuatorState actuatorState;


    // Costruttore Vuoto (Necessario per JPA)
    public AutomationRule() {}

    // Costruttore Completo
    public AutomationRule(Long id, String sensorName, String operator, Double value, String actuatorName, ActuatorState actuatorState) {
        this.id = id;
        this.sensorName = sensorName;
        this.operator = operator;
        this.value = value;
        this.actuatorName = actuatorName;
        this.actuatorState = actuatorState;
    }

    // Getter e Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getsensorName() { return sensorName; }
    public void setsensorName(String sensorName) { this.sensorName = sensorName; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public Double getvalue() { return value; }
    public void setvalue(Double value) { this.value = value; }

    public String getactuatorName() { return actuatorName; }
    public void setactuatorName(String actuatorName) { this.actuatorName = actuatorName; }

    public ActuatorState getActuatorState() {return actuatorState;}
    public void setActuatorState(ActuatorState actuatorState) {this.actuatorState = actuatorState;}
}
