package com.pablomarotta.smart_task_manager.dto.planning;

public record PlanningProjectSnapshot(
        Long id,
        String name,
        String objective
) {
}
