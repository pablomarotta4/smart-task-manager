package com.pablomarotta.smart_task_manager.dto.planning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProjectPlanDraft(
        @NotBlank @Size(min = 3, max = 150) String name,
        @NotBlank @Size(min = 20, max = 2000) String objective,
        @NotNull @Size(max = 10) List<@NotBlank @Size(min = 3, max = 255) String> assumptions,
        @NotNull @Size(max = 10) List<@NotBlank @Size(min = 3, max = 255) String> risks,
        @NotNull @Size(min = 3, max = 12) List<@Valid PlanningTicketDraft> tickets
) {
}
