package com.pablomarotta.smart_task_manager.dto.planning;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;

import java.time.LocalDate;
import java.util.List;

public record PlanningTaskSnapshot(
        Long id,
        String title,
        String description,
        Status status,
        Priority priority,
        String category,
        @JsonProperty("due_date") LocalDate dueDate,
        Integer position,
        @JsonProperty("assignee_id") Long assigneeId,
        @JsonProperty("assignee_username") String assigneeUsername,
        @JsonProperty("acceptance_criteria") List<String> acceptanceCriteria,
        @JsonProperty("depends_on_task_ids") List<Long> dependsOnTaskIds
) {
}
