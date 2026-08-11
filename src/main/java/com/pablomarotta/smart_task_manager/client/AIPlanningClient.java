package com.pablomarotta.smart_task_manager.client;

import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningResponse;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningContext;

import java.util.UUID;

public interface AIPlanningClient {
    default AIPlanningResponse generatePlan(UUID runId, String prompt) {
        return generatePlan(runId, prompt, null);
    }

    AIPlanningResponse generatePlan(UUID runId, String prompt, AIPlanningContext context);
}
