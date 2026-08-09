package com.pablomarotta.smart_task_manager.dto.planning;

import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;

import java.util.UUID;

public record ProjectGenerationDraftResponse(
        UUID runId,
        ProjectGenerationStatus status,
        ProjectPlanDraft draft,
        PlanQualityReport quality,
        int revisionCount,
        String model
) {
}
