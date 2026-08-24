package com.pablomarotta.smart_task_manager.dto.planning;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlanQualityMetrics(
        @JsonProperty("ticket_count") int ticketCount,
        @JsonProperty("unique_title_ratio") double uniqueTitleRatio,
        @JsonProperty("max_title_similarity") double maxTitleSimilarity,
        @JsonProperty("description_coverage") double descriptionCoverage,
        @JsonProperty("acceptance_criteria_coverage") double acceptanceCriteriaCoverage
) {
}
