package com.pablomarotta.smart_task_manager.client;

import com.pablomarotta.smart_task_manager.config.AIPlanningProperties;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningResponse;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningContext;
import com.pablomarotta.smart_task_manager.dto.planning.PlanningProjectSnapshot;
import com.pablomarotta.smart_task_manager.dto.planning.PlanningTaskSnapshot;
import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;
import com.pablomarotta.smart_task_manager.model.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FastApiAIPlanningClientTest {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private MockRestServiceServer server;
    private FastApiAIPlanningClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AIPlanningProperties properties = new AIPlanningProperties();
        properties.setBaseUrl("http://ai-service:8000");
        client = new FastApiAIPlanningClient(builder.baseUrl(properties.getBaseUrl()).build());
    }

    @Test
    void generatePlanSerializesV1ContractAndParsesStrictResponse() {
        UUID runId = UUID.fromString("4cc8ab44-1d91-4b12-96ac-cba3824a7907");
        server.expect(once(), requestTo("http://ai-service:8000/internal/v1/project-plans"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "contract_version": "v1",
                          "run_id": "4cc8ab44-1d91-4b12-96ac-cba3824a7907",
                          "prompt": "Build a useful household budget application"
                        }
                        """))
                .andRespond(withSuccess(successResponse(runId), MediaType.APPLICATION_JSON));

        AIPlanningResponse response = client.generatePlan(
                runId,
                "Build a useful household budget application"
        );

        assertEquals(runId, response.runId());
        assertEquals("Budget App", response.draft().name());
        assertEquals(
                List.of("Should the first release support more than one household?"),
                response.draft().openQuestions()
        );
        assertEquals(3, response.draft().tickets().size());
        assertEquals(100, response.quality().score());
        assertEquals("fake-model", response.model());
        server.verify();
    }

    @Test
    void generatePlanPropagatesRequestCorrelationId() {
        UUID runId = UUID.fromString("4cc8ab44-1d91-4b12-96ac-cba3824a7907");
        MDC.put(CORRELATION_ID_MDC_KEY, "web-request-2026-08-12");
        server.expect(once(), requestTo("http://ai-service:8000/internal/v1/project-plans"))
                .andExpect(header(CORRELATION_ID_HEADER, "web-request-2026-08-12"))
                .andRespond(withSuccess(successResponse(runId), MediaType.APPLICATION_JSON));

        try {
            client.generatePlan(runId, "Build a useful household budget application");
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }

        server.verify();
    }

    @Test
    void generatePlanReplacesUnsafeRequestCorrelationId() {
        UUID runId = UUID.fromString("4cc8ab44-1d91-4b12-96ac-cba3824a7907");
        String unsafeCorrelationId = "secret correlation value\nforged-header";
        MDC.put(CORRELATION_ID_MDC_KEY, unsafeCorrelationId);
        server.expect(once(), requestTo("http://ai-service:8000/internal/v1/project-plans"))
                .andExpect(request -> {
                    String outboundCorrelationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
                    assertNotNull(outboundCorrelationId);
                    assertNotEquals(unsafeCorrelationId, outboundCorrelationId);
                    assertEquals(4, UUID.fromString(outboundCorrelationId).version());
                })
                .andRespond(withSuccess(successResponse(runId), MediaType.APPLICATION_JSON));

        try {
            client.generatePlan(runId, "Build a useful household budget application");
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }

        server.verify();
    }

    @Test
    void generatePlanMapsUpstreamFailure() {
        server.expect(once(), requestTo("http://ai-service:8000/internal/v1/project-plans"))
                .andRespond(withResourceNotFound());

        assertThrows(
                AIPlanningUnavailableException.class,
                () -> client.generatePlan(UUID.randomUUID(), "Build a useful household budget application")
        );
    }

    @Test
    void generatePlanSerializesExistingTaskContext() {
        UUID runId = UUID.fromString("4cc8ab44-1d91-4b12-96ac-cba3824a7907");
        AIPlanningContext context = new AIPlanningContext(
                ProjectGenerationMode.EXISTING_TASK,
                new PlanningProjectSnapshot(20L, "Budget App", "Manage a household budget"),
                201L,
                List.of(new PlanningTaskSnapshot(
                        201L,
                        "Import bank transactions",
                        "Import normalized transactions from a bank export.",
                        Status.TODO,
                        Priority.HIGH,
                        "IMPORT",
                        null,
                        0,
                        null,
                        null,
                        List.of(),
                        List.of()
                ))
        );
        server.expect(once(), requestTo("http://ai-service:8000/internal/v1/project-plans"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "contract_version": "v1",
                          "run_id": "4cc8ab44-1d91-4b12-96ac-cba3824a7907",
                          "prompt": "Break this ticket into implementation steps",
                          "context": {
                            "mode": "EXISTING_TASK",
                            "project": {"id": 20, "name": "Budget App"},
                            "selected_task_id": 201,
                            "tasks": [{"id": 201, "title": "Import bank transactions"}]
                          }
                        }
                        """, false))
                .andRespond(withSuccess(successResponse(runId), MediaType.APPLICATION_JSON));

        AIPlanningResponse response = client.generatePlan(
                runId,
                "Break this ticket into implementation steps",
                context
        );

        assertEquals(runId, response.runId());
        server.verify();
    }

    private String successResponse(UUID runId) {
        return """
                {
                  "contract_version": "v1",
                  "run_id": "%s",
                  "draft": {
                    "name": "Budget App",
                    "objective": "Help a household understand and manage its monthly budget.",
                    "assumptions": ["The first version supports one household"],
                    "risks": ["Expense data may be incomplete"],
                    "open_questions": ["Should the first release support more than one household?"],
                    "tickets": [
                      {
                        "client_id": "accounts",
                        "title": "Create household accounts",
                        "description": "Deliver account creation with validation and automated behavior coverage.",
                        "priority": "HIGH",
                        "estimated_hours": 4,
                        "acceptance_criteria": ["A user can create an account", "Invalid accounts are rejected"],
                        "depends_on": [],
                        "category": "FOUNDATION",
                        "due_in_days": 3
                      },
                      {
                        "client_id": "expenses",
                        "title": "Record categorized expenses",
                        "description": "Deliver categorized expense entry with validation and automated behavior coverage.",
                        "priority": "MEDIUM",
                        "estimated_hours": 6,
                        "acceptance_criteria": ["A user can record an expense", "Invalid amounts are rejected"],
                        "depends_on": ["accounts"],
                        "category": "FEATURE",
                        "due_in_days": 7
                      },
                      {
                        "client_id": "summary",
                        "title": "Review monthly spending",
                        "description": "Deliver a monthly spending summary with useful empty and error states.",
                        "priority": "MEDIUM",
                        "estimated_hours": 5,
                        "acceptance_criteria": ["Totals match recorded expenses", "Empty months are clearly explained"],
                        "depends_on": ["expenses"],
                        "category": "FEATURE",
                        "due_in_days": 10
                      }
                    ]
                  },
                  "quality": {
                    "score": 100,
                    "passed": true,
                    "issues": [],
                    "metrics": {
                      "ticket_count": 3,
                      "unique_title_ratio": 1.0,
                      "max_title_similarity": 0.4,
                      "description_coverage": 1.0,
                      "acceptance_criteria_coverage": 1.0
                    }
                  },
                  "revision_count": 0,
                  "model": "fake-model"
                }
                """.formatted(runId);
    }
}
