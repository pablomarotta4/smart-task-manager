package com.pablomarotta.smart_task_manager.dto.planning;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;

import java.util.List;

public record AIPlanningContext(
        ProjectGenerationMode mode,
        PlanningProjectSnapshot project,
        @JsonProperty("selected_task_id") Long selectedTaskId,
        List<PlanningTaskSnapshot> tasks
) {
}
