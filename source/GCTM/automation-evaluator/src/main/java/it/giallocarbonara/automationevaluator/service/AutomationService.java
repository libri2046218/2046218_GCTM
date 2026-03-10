package it.giallocarbonara.automationevaluator.service;

import it.giallocarbonara.AutomRule;
import it.giallocarbonara.SensorData;
import it.giallocarbonara.automationevaluator.entity.AutomationRule;
import it.giallocarbonara.automationevaluator.producer.CommandProducer;
import it.giallocarbonara.automationevaluator.repository.RuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutomationService {

    private final CommandProducer commandProducer;
    private final RuleRepository ruleRepository;

    public AutomationService(CommandProducer commandProducer, RuleRepository ruleRepository) {
        this.commandProducer = commandProducer;
        this.ruleRepository = ruleRepository;
    }

    public void createNewRule(AutomRule rule) {
        String sensorName = rule.sensorName();
        String operator = rule.operator();
        Double value = rule.value();
        String actuatorName = rule.actuatorName();
        String actuatorState = rule.actuatorState();
        Boolean manualOverride = rule.manualOverride();
        AutomationRule newRule = new AutomationRule(null, sensorName, operator, value, actuatorName, actuatorState, manualOverride);
        ruleRepository.save(newRule);
    }


    public void deleteRule(AutomRule rule) {
        String sensorName = rule.sensorName();
        String operator = rule.operator();
        Double value = rule.value();
        String actuatorName = rule.actuatorName();
        String actuatorState = rule.actuatorState();
        Boolean manualOverride = rule.manualOverride();

        System.out.println("[AutomationService] ⚠️ DELETE REQUEST RECEIVED: " +
            "IF " + sensorName + " " + operator + " " + value + " THEN SET " + actuatorName + " TO " + actuatorState);

        List<AutomationRule> rulesToDelete = ruleRepository
            .findBySensorNameAndOperatorAndValueAndActuatorNameAndActuatorStateAndManualOverride(
                sensorName,
                operator,
                value,
                actuatorName,
                actuatorState,
                manualOverride
            );

        System.out.println("[AutomationService] Found " + rulesToDelete.size() + " rule(s) matching deletion criteria");

        for (AutomationRule r : rulesToDelete) {
            System.out.println("[AutomationService] 🗑️ Deleting Rule ID: " + r.getId() + 
                " | Sensor: " + r.getSensorName() + ", Operator: " + r.getOperator() + 
                ", Value: " + r.getValue());
            ruleRepository.delete(r);
        }

        System.out.println("[AutomationService] ✅ Deletion completed. " + rulesToDelete.size() + " rule(s) deleted");
    }

    public void fetchRules() {
        List<AutomationRule> rules = ruleRepository.findAll();
        for (AutomationRule rule : rules) {
            commandProducer.sendRule(rule);
        }

    }

    public void evaluate(SensorData sensorData) {

        List<AutomationRule> rules = ruleRepository.findBySensorNameIgnoreCase(sensorData.sensor_id());
        for (AutomationRule rule : rules) {
            if(rule.getManualOverride() == Boolean.TRUE) {continue;}
            if (checkCondition(rule, sensorData.metrics().getFirst().value())) {
                triggerAction(rule.getActuatorName(), rule.getActuatorState());
            }
        }

    }

    private boolean checkCondition(AutomationRule rule, Object value) {
        if (!(value instanceof Number num)) return false;
        double currentVal = num.doubleValue();

        return switch (rule.getOperator()) {
            case "<" -> currentVal < rule.getValue();
            case ">" -> currentVal > rule.getValue();
            case "==" -> currentVal == rule.getValue();
            default -> false;
        };
    }

    private void triggerAction(String actuatorName, String action) {
        System.out.println("⚠️ ACTION: " + action + " for " + actuatorName);
        commandProducer.sendCommand(actuatorName, action);
    }
}