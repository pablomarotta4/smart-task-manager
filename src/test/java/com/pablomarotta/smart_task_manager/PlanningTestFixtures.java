package com.pablomarotta.smart_task_manager;

import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningResponse;
import com.pablomarotta.smart_task_manager.dto.planning.PlanQualityMetrics;
import com.pablomarotta.smart_task_manager.dto.planning.PlanQualityReport;
import com.pablomarotta.smart_task_manager.dto.planning.PlanningTicketDraft;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectPlanDraft;
import com.pablomarotta.smart_task_manager.model.Priority;

import java.util.List;
import java.util.UUID;

public final class PlanningTestFixtures {
    private PlanningTestFixtures() {
    }

    public static AIPlanningResponse response(UUID runId) {
        return new AIPlanningResponse(
                "v1",
                runId,
                draft(),
                new PlanQualityReport(
                        100,
                        true,
                        List.of(),
                        new PlanQualityMetrics(3, 1, 0.4, 1, 1)
                ),
                0,
                "fake-model"
        );
    }

    public static ProjectPlanDraft draft() {
        return new ProjectPlanDraft(
                "Budget App",
                "Help a household understand and manage its monthly budget.",
                List.of("The first version supports one household"),
                List.of("Expense data may be incomplete"),
                List.of("Should the first release support more than one household?"),
                List.of(
                        ticket("accounts", "Create household accounts", List.of()),
                        ticket("expenses", "Record categorized expenses", List.of("accounts")),
                        ticket("summary", "Review monthly spending", List.of("expenses"))
                )
        );
    }

    private static PlanningTicketDraft ticket(String clientId, String title, List<String> dependsOn) {
        return new PlanningTicketDraft(
                clientId,
                title,
                "Deliver " + title.toLowerCase() + " with validation and automated behavior coverage.",
                Priority.MEDIUM,
                4.0,
                List.of("A user can complete the behavior", "Invalid input is rejected clearly"),
                dependsOn,
                "FEATURE",
                7
        );
    }
}
