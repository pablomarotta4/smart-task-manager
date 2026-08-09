package com.pablomarotta.smart_task_manager.dto.planning;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AIPlanningRequest(
        @JsonProperty("contract_version") String contractVersion,
        @JsonProperty("run_id") UUID runId,
        String prompt
) {
    public AIPlanningRequest(UUID runId, String prompt) {
        this("v1", runId, prompt);
    }
}
