package it.giallocarbonara.automationevaluator.repository;

import it.giallocarbonara.automationevaluator.entity.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RuleRepository extends JpaRepository<AutomationRule, Long> {
    List<AutomationRule> findByMetricNameIgnoreCase(String metricName);
}