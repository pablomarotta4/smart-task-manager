package com.pablomarotta.smart_task_manager.dto.planning;

import java.util.List;

public record PlanQualityReport(
        int score,
        boolean passed,
        List<PlanQualityIssue> issues,
        PlanQualityMetrics metrics
) {
}
