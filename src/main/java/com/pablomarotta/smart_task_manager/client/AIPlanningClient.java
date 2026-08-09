package com.pablomarotta.smart_task_manager.client;

import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningResponse;

import java.util.UUID;

public interface AIPlanningClient {
    AIPlanningResponse generatePlan(UUID runId, String prompt);
}
