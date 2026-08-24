package com.pablomarotta.smart_task_manager.dto.planning;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PlanQualityIssue(
        String code,
        String message,
        @JsonProperty("ticket_ids") List<String> ticketIds
) {
}
