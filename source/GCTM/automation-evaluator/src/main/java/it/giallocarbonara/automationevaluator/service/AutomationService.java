package it.giallocarbonara.automationevaluator.service;

import it.giallocarbonara.AutomRule;
import it.giallocarbonara.Metric;
import it.giallocarbonara.SensorData;
import it.giallocarbonara.automationevaluator.entity.AutomationRule;
import it.giallocarbonara.automationevaluator.producer.CommandProducer;
import it.giallocarbonara.automationevaluator.repository.RuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutomationService {

    // TODO: Implement:
    // -  Return all rules from database (for initial frontend load)
    // - Delete rule from database
    // This will allow the frontend (web-engine) to synchronize with the backend database
    // and ensure rules persist across browser sessions.
    
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