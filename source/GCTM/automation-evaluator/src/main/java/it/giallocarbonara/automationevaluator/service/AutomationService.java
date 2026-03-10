package it.giallocarbonara.automationevaluator.service;

import it.giallocarbonara.AutomRule;
import it.giallocarbonara.Metric;
import it.giallocarbonara.SensorData;
import it.giallocarbonara.automationevaluator.entity.AutomationRule;
import it.giallocarbonara.automationevaluator.producer.CommandProducer;
import it.giallocarbonara.automationevaluator.repository.RuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

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
        String metricName = rule.metricName();
        String operator = rule.operator();
        Double value = rule.value();
        String valueText = normalizeValueText(rule.valueText());
        String actuatorName = rule.actuatorName();
        String actuatorState = rule.actuatorState();
        Boolean manualOverride = rule.manualOverride();
        AutomationRule newRule = new AutomationRule(null, sensorName, metricName, operator, value, valueText, actuatorName, actuatorState, manualOverride);
        ruleRepository.save(newRule);
    }


    public void deleteRule(AutomRule rule) {
        String sensorName = rule.sensorName();
        String metricName = rule.metricName();
        String operator = rule.operator();
        Double value = rule.value();
        String valueText = normalizeValueText(rule.valueText());
        String actuatorName = rule.actuatorName();
        String actuatorState = rule.actuatorState();
        Boolean manualOverride = rule.manualOverride();

        System.out.println("[AutomationService] ⚠️ DELETE REQUEST RECEIVED: " +
            "IF " + sensorName + " (metric: " + metricName + ") " + operator + " " + valueText + " THEN SET " + actuatorName + " TO " + actuatorState);

        List<AutomationRule> rulesToDelete = ruleRepository
            .findBySensorNameAndOperatorAndActuatorNameAndActuatorStateAndManualOverride(
                sensorName,
                operator,
                actuatorName,
                actuatorState,
                manualOverride
            )
            .stream()
            .filter(existingRule -> 
                (metricName == null ? existingRule.getMetricName() == null : metricName.equalsIgnoreCase(existingRule.getMetricName() != null ? existingRule.getMetricName() : "")) &&
                thresholdMatches(existingRule, value, valueText)
            )
            .toList();

        System.out.println("[AutomationService] Found " + rulesToDelete.size() + " rule(s) matching deletion criteria");

        for (AutomationRule r : rulesToDelete) {
            System.out.println("[AutomationService] 🗑️ Deleting Rule ID: " + r.getId() + 
                " | Sensor: " + r.getSensorName() + " | Metric: " + r.getMetricName() + ", Operator: " + r.getOperator() + 
                ", Value: " + displayRuleThreshold(r));
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
            
            // Find the metric by name, or use first if no specific metric is defined
            Object metricValue = getMetricValue(sensorData.metrics(), rule.getMetricName());
            if (metricValue == null) {
                System.out.println("[AutomationService] ⚠️ Metric '" + rule.getMetricName() + "' not found for sensor '" + sensorData.sensor_id() + "'");
                continue;
            }
            
            if (checkCondition(rule, metricValue)) {
                triggerAction(rule.getActuatorName(), rule.getActuatorState());
            }
        }

    }

    private Object getMetricValue(List<Metric> metrics, String metricName) {
        if (metricName == null || metricName.isBlank()) {
            // If no metric name specified, use the first metric (for backward compatibility)
            return metrics.isEmpty() ? null : metrics.getFirst().value();
        }
        
        // Find metric by exact name match
        return metrics.stream()
            .filter(m -> m.name().equalsIgnoreCase(metricName))
            .map(Metric::value)
            .findFirst()
            .orElse(null);
    }

    private boolean checkCondition(AutomationRule rule, Object value) {
        if (value instanceof Number num) {
            Double ruleValue = resolveNumericRuleValue(rule);
            if (ruleValue == null) return false;

            double currentVal = num.doubleValue();

            return switch (rule.getOperator()) {
                case "<" -> currentVal < ruleValue;
                case ">" -> currentVal > ruleValue;
                case "<=" -> currentVal <= ruleValue;
                case ">=" -> currentVal >= ruleValue;
                case "==" -> Double.compare(currentVal, ruleValue) == 0;
                case "!=" -> Double.compare(currentVal, ruleValue) != 0;
                default -> false;
            };
        }

        if (!"==".equals(rule.getOperator())) return false;

        String currentValue = normalizeValueText(String.valueOf(value));
        String expectedValue = resolveTextRuleValue(rule);
        return currentValue != null && expectedValue != null && currentValue.equalsIgnoreCase(expectedValue);
    }

    private boolean thresholdMatches(AutomationRule existingRule, Double requestedValue, String requestedValueText) {
        if (requestedValue != null && existingRule.getValue() != null && Double.compare(existingRule.getValue(), requestedValue) == 0) {
            return true;
        }

        String existingValueText = resolveTextRuleValue(existingRule);
        return existingValueText != null
                && requestedValueText != null
                && existingValueText.equalsIgnoreCase(requestedValueText);
    }

    private Double resolveNumericRuleValue(AutomationRule rule) {
        if (rule.getValue() != null) return rule.getValue();

        String valueText = normalizeValueText(rule.getValueText());
        if (valueText == null) return null;

        try {
            return Double.parseDouble(valueText);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String resolveTextRuleValue(AutomationRule rule) {
        String valueText = normalizeValueText(rule.getValueText());
        if (valueText != null) return valueText;
        return rule.getValue() != null ? String.valueOf(rule.getValue()) : null;
    }

    private String normalizeValueText(String valueText) {
        if (valueText == null) return null;
        String trimmed = valueText.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String displayRuleThreshold(AutomationRule rule) {
        return Objects.toString(resolveTextRuleValue(rule), "null");
    }

    private void triggerAction(String actuatorName, String action) {
        System.out.println("⚠️ ACTION: " + action + " for " + actuatorName);
        commandProducer.sendCommand(actuatorName, action);
    }
}