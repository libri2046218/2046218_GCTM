package it.giallocarbonara.automationevaluator.service;

import it.giallocarbonara.UnifiedEnvelope;
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

    public void createNewRule(UnifiedEnvelope envelope) {
        //TODO: Unwrap envelope
        String sensorName = "";
        String operator = "";
        Double value = 0.0;
        String actuatorName = "";
        AutomationRule.ActuatorState actuatorState = AutomationRule.ActuatorState.OFF;
        AutomationRule newRule = new AutomationRule(null, sensorName, operator, value, actuatorName, actuatorState);
        ruleRepository.save(newRule);
    }

    public void evaluate(UnifiedEnvelope envelope) {
        for (UnifiedEnvelope.Metric metric : envelope.payload().metrics()) {

            List<AutomationRule> rules = ruleRepository.findByMetricNameIgnoreCase(metric.name());

            for (AutomationRule rule : rules) {
                if (checkCondition(rule, metric.value())) {
                    triggerAction(envelope.payload().subject_id(), rule.getActuatorState());
                }
            }
        }
    }

    private boolean checkCondition(AutomationRule rule, Object value) {
        if (!(value instanceof Number num)) return false;
        double currentVal = num.doubleValue();

        return switch (rule.getOperator()) {
            case "<" -> currentVal < rule.getvalue();
            case ">" -> currentVal > rule.getvalue();
            case "==" -> currentVal == rule.getvalue();
            default -> false;
        };
    }

    private void triggerAction(String actuatorName, AutomationRule.ActuatorState action) {
        System.out.println("⚠️ ACTION: " + action + " for " + actuatorName);
        commandProducer.sendCommand(actuatorName, action);
    }
}