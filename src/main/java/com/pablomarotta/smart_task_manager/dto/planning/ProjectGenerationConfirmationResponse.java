package com.pablomarotta.smart_task_manager.dto.planning;

import java.util.List;
import java.util.UUID;

public record ProjectGenerationConfirmationResponse(
        UUID runId,
        Long projectId,
        String projectName,
        List<Long> taskIds,
        boolean alreadyConfirmed
) {
}
