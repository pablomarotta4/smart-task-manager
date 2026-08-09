package com.pablomarotta.smart_task_manager.dto.planning;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AIPlanningResponse(
        @JsonProperty("contract_version") String contractVersion,
        @JsonProperty("run_id") UUID runId,
        ProjectPlanDraft draft,
        PlanQualityReport quality,
        @JsonProperty("revision_count") int revisionCount,
        String model
) {
}
