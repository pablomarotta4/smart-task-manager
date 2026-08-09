package com.pablomarotta.smart_task_manager.dto.planning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateProjectRequest(
        @NotBlank @Size(min = 10, max = 4000) String prompt
) {
}
