package com.pablomarotta.smart_task_manager.dto.planning;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectGenerationRunDetailResponse(
        UUID runId,
        ProjectGenerationMode mode,
        ProjectGenerationStatus status,
        String prompt,
        int attemptCount,
        Long projectId,
        String projectName,
        Long targetTaskId,
        String targetTaskTitle,
        String errorCode,
        boolean retryable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        ProjectPlanDraft draft,
        PlanQualityReport quality,
        Integer revisionCount,
        String model
) {
}
