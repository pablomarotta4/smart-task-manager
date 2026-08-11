package com.pablomarotta.smart_task_manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.PlanningTestFixtures;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationConfirmationResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationDraftResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationRunDetailResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationRunSummaryResponse;
import com.pablomarotta.smart_task_manager.exception.GlobalExceptionHandler;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;
import com.pablomarotta.smart_task_manager.service.ProjectGenerationConfirmationService;
import com.pablomarotta.smart_task_manager.service.ProjectGenerationRunQueryService;
import com.pablomarotta.smart_task_manager.service.ProjectGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationControllerTest {

    @Mock
    private ProjectGenerationService generationService;
    @Mock
    private ProjectGenerationConfirmationService confirmationService;
    @Mock
    private ProjectGenerationRunQueryService queryService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID runId;

    @BeforeEach
    void setUp() {
        ProjectGenerationController controller = new ProjectGenerationController(
                generationService,
                confirmationService,
                queryService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        runId = UUID.randomUUID();
    }

    @Test
    void listsOnlyTheAuthenticatedUsersRecentRuns() throws Exception {
        when(queryService.listRecent("alice")).thenReturn(List.of(
                new ProjectGenerationRunSummaryResponse(
                        runId,
                        ProjectGenerationMode.EXISTING_TASK,
                        ProjectGenerationStatus.DRAFT_READY,
                        "Break this ticket into implementation steps",
                        1,
                        20L,
                        "Budget App",
                        201L,
                        "Import transactions",
                        null,
                        false,
                        LocalDateTime.of(2026, 8, 11, 10, 0),
                        LocalDateTime.of(2026, 8, 11, 10, 1)
                )
        ));

        mockMvc.perform(get("/api/project-generation-runs")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "ignored")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value(runId.toString()))
                .andExpect(jsonPath("$[0].mode").value("EXISTING_TASK"))
                .andExpect(jsonPath("$[0].targetTaskId").value(201));

        verify(queryService).listRecent("alice");
    }

    @Test
    void restoresOnePersistedDraftForTheAuthenticatedUser() throws Exception {
        var aiResponse = PlanningTestFixtures.response(runId);
        when(queryService.get(runId, "alice")).thenReturn(
                new ProjectGenerationRunDetailResponse(
                        runId,
                        ProjectGenerationMode.NEW_PROJECT,
                        ProjectGenerationStatus.DRAFT_READY,
                        "Build a useful household budget application",
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        LocalDateTime.of(2026, 8, 11, 10, 0),
                        LocalDateTime.of(2026, 8, 11, 10, 1),
                        aiResponse.draft(),
                        aiResponse.quality(),
                        aiResponse.revisionCount(),
                        aiResponse.model()
                )
        );

        mockMvc.perform(get("/api/project-generation-runs/{runId}", runId)
                        .principal(new UsernamePasswordAuthenticationToken("alice", "ignored")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.draft.name").value("Budget App"))
                .andExpect(jsonPath("$.quality.score").value(100));

        verify(queryService).get(runId, "alice");
    }

    @Test
    void generateUsesAuthenticatedPrincipalAndReturnsEditableDraft() throws Exception {
        var aiResponse = PlanningTestFixtures.response(runId);
        when(generationService.generateDraft(eq("alice"), any(String.class))).thenReturn(
                new ProjectGenerationDraftResponse(
                        runId,
                        ProjectGenerationStatus.DRAFT_READY,
                        aiResponse.draft(),
                        aiResponse.quality(),
                        aiResponse.revisionCount(),
                        aiResponse.model()
                )
        );

        mockMvc.perform(post("/api/project-generation-runs")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "ignored"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Build a useful household budget application"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT_READY"))
                .andExpect(jsonPath("$.draft.name").value("Budget App"))
                .andExpect(jsonPath("$.draft.tickets.length()").value(3))
                .andExpect(jsonPath("$.quality.passed").value(true));

        verify(generationService).generateDraft(
                "alice", "Build a useful household budget application"
        );
    }

    @Test
    void invalidPromptIsRejectedBeforeGeneration() throws Exception {
        mockMvc.perform(post("/api/project-generation-runs")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "ignored"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"tiny\"}"))
                .andExpect(status().isBadRequest());

        verify(generationService, never()).generateDraft(any(), any());
    }

    @Test
    void generateForExistingTaskUsesAuthenticatedProjectScopedTarget() throws Exception {
        var aiResponse = PlanningTestFixtures.response(runId);
        when(generationService.generateDraftForTask(
                eq("alice"), eq(20L), eq(201L), any(String.class)
        )).thenReturn(new ProjectGenerationDraftResponse(
                runId,
                ProjectGenerationStatus.DRAFT_READY,
                aiResponse.draft(),
                aiResponse.quality(),
                aiResponse.revisionCount(),
                aiResponse.model()
        ));

        mockMvc.perform(post(
                        "/api/project-generation-runs/projects/{projectId}/tasks/{taskId}",
                        20L,
                        201L
                )
                        .principal(new UsernamePasswordAuthenticationToken("alice", "ignored"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Break this ticket into implementation steps"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT_READY"));

        verify(generationService).generateDraftForTask(
                "alice",
                20L,
                201L,
                "Break this ticket into implementation steps"
        );
    }

    @Test
    void confirmAcceptsEditedDraftAndReturnsCreatedProjectAndTickets() throws Exception {
        when(confirmationService.confirm(eq(runId), eq("alice"), any())).thenReturn(
                new ProjectGenerationConfirmationResponse(
                        runId,
                        41L,
                        "Edited Budget App",
                        List.of(100L, 101L, 102L),
                        false
                )
        );
        var draft = PlanningTestFixtures.draft();
        var editedDraft = new com.pablomarotta.smart_task_manager.dto.planning.ProjectPlanDraft(
                "Edited Budget App",
                draft.objective(),
                draft.assumptions(),
                draft.risks(),
                draft.tickets()
        );

        mockMvc.perform(post("/api/project-generation-runs/{runId}/confirm", runId)
                        .principal(new UsernamePasswordAuthenticationToken("alice", "ignored"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("draft", editedDraft))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(41))
                .andExpect(jsonPath("$.projectName").value("Edited Budget App"))
                .andExpect(jsonPath("$.taskIds.length()").value(3))
                .andExpect(jsonPath("$.alreadyConfirmed").value(false));

        verify(confirmationService).confirm(runId, "alice", editedDraft);
    }
}
