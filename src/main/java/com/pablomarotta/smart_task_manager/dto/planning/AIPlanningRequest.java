package com.pablomarotta.smart_task_manager.dto.planning;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AIPlanningRequest(
        @JsonProperty("contract_version") String contractVersion,
        @JsonProperty("run_id") UUID runId,
        String prompt,
        AIPlanningContext context
) {
    public AIPlanningRequest(UUID runId, String prompt) {
        this("v1", runId, prompt, null);
    }

    public AIPlanningRequest(UUID runId, String prompt, AIPlanningContext context) {
        this("v1", runId, prompt, context);
    }
}
