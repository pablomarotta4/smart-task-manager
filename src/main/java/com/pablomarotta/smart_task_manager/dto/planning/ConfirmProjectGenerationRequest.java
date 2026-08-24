package com.pablomarotta.smart_task_manager.dto.planning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ConfirmProjectGenerationRequest(
        @NotNull @Valid ProjectPlanDraft draft
) {
}
