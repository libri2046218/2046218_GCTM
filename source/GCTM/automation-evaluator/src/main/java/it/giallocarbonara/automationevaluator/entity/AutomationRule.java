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

    @Column(name = "metric_name")
    private String metricName;

    @Column(name = "operator")
    private String operator; // es. "<", ">"

    @Column(name = "value")
    private Double value;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "actuator_name")
    private String actuatorName;

    @Column(name = "actuator_state")
    private String actuatorState;

    @Column(name = "manualOverride")
    private Boolean manualOverride;


    // Costruttore Vuoto (Necessario per JPA)
    public AutomationRule() {}

    // Costruttore Completo
    public AutomationRule(Long id, String sensorName, String metricName, String operator, Double value, String actuatorName, String actuatorState, Boolean manualOverride) {
        this(id, sensorName, metricName, operator, value, null, actuatorName, actuatorState, manualOverride);
    }

    public AutomationRule(Long id, String sensorName, String metricName, String operator, Double value, String valueText, String actuatorName, String actuatorState, Boolean manualOverride) {
        this.id = id;
        this.sensorName = sensorName;
        this.metricName = metricName;
        this.operator = operator;
        this.value = value;
        this.valueText = valueText;
        this.actuatorName = actuatorName;
        this.actuatorState = actuatorState;
        this.manualOverride = manualOverride;
    }

    // Getter e Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSensorName() { return sensorName; }
    public void setSensorName(String sensorName) { this.sensorName = sensorName; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }

    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }

    public String getActuatorName() { return actuatorName; }
    public void setActuatorName(String actuatorName) { this.actuatorName = actuatorName; }

    public String getActuatorState() {return actuatorState;}
    public void setActuatorState(String actuatorState) {this.actuatorState = actuatorState;}

    public Boolean getManualOverride() {return manualOverride;}
    public void setManualOverride(Boolean manualOverride) {this.manualOverride = manualOverride;}
}
