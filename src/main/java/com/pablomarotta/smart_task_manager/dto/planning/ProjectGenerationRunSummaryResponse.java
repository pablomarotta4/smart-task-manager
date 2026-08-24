package com.pablomarotta.smart_task_manager.dto.planning;

import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectGenerationRunSummaryResponse(
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
        LocalDateTime updatedAt
) {
}
