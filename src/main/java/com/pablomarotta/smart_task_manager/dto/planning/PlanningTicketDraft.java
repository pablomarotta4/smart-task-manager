package com.pablomarotta.smart_task_manager.dto.planning;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pablomarotta.smart_task_manager.model.Priority;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PlanningTicketDraft(
        @JsonProperty("client_id")
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9-]{0,49}$")
        String clientId,

        @NotBlank
        @Size(min = 5, max = 120)
        String title,

        @NotBlank
        @Size(min = 20, max = 2000)
        String description,

        @NotNull
        Priority priority,

        @JsonProperty("estimated_hours")
        @NotNull
        @DecimalMin(value = "0.1")
        @DecimalMax(value = "80")
        Double estimatedHours,

        @JsonProperty("acceptance_criteria")
        @NotEmpty
        @Size(max = 8)
        List<@NotBlank @Size(min = 10, max = 500) String> acceptanceCriteria,

        @JsonProperty("depends_on")
        @NotNull
        @Size(max = 6)
        List<@Pattern(regexp = "^[a-z][a-z0-9-]{0,49}$") String> dependsOn,

        @Size(min = 2, max = 32)
        String category,

        @JsonProperty("due_in_days")
        @Min(0)
        @Max(365)
        Integer dueInDays
) {
}
