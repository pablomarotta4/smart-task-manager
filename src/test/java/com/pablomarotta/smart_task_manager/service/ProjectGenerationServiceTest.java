package com.pablomarotta.smart_task_manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.PlanningTestFixtures;
import com.pablomarotta.smart_task_manager.client.AIPlanningClient;
import com.pablomarotta.smart_task_manager.client.AIPlanningUnavailableException;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningResponse;
import com.pablomarotta.smart_task_manager.dto.planning.PlanQualityMetrics;
import com.pablomarotta.smart_task_manager.dto.planning.PlanQualityReport;
import com.pablomarotta.smart_task_manager.dto.planning.PlanningTicketDraft;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationDraftResponse;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningContext;
import com.pablomarotta.smart_task_manager.dto.planning.PlanningProjectSnapshot;
import com.pablomarotta.smart_task_manager.dto.planning.PlanningTaskSnapshot;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectPlanDraft;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationRun;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectGenerationRunRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationServiceTest {

    @Mock
    private ProjectGenerationRunRepository runRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AIPlanningClient aiPlanningClient;
    @Mock
    private ProjectPlanningContextService contextService;
    @Mock
    private ProjectGenerationRunRetryClaimService retryClaimService;

    private ProjectGenerationService service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new ProjectGenerationService(
                runRepository,
                userRepository,
                aiPlanningClient,
                contextService,
                retryClaimService,
                new ObjectMapper().findAndRegisterModules()
        );
        owner = User.builder().id(7L).username("alice").email("alice@example.com")
                .password("encoded").fullName("Alice").build();
        when(runRepository.save(any(ProjectGenerationRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void generateDraftUsesAuthenticatedOwnerAndPersistsDraftWithoutApplyingIt() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(aiPlanningClient.generatePlan(any(UUID.class), any(String.class)))
                .thenAnswer(invocation -> PlanningTestFixtures.response(invocation.getArgument(0)));

        ProjectGenerationDraftResponse result = service.generateDraft(
                "alice",
                "Build a useful household budget application"
        );

        assertNotNull(result.runId());
        assertEquals(ProjectGenerationStatus.DRAFT_READY, result.status());
        assertEquals("Budget App", result.draft().name());
        assertEquals(100, result.quality().score());

        ArgumentCaptor<ProjectGenerationRun> runCaptor = ArgumentCaptor.forClass(ProjectGenerationRun.class);
        verify(runRepository, org.mockito.Mockito.times(2)).save(runCaptor.capture());
        ProjectGenerationRun stored = runCaptor.getValue();
        assertEquals(owner, stored.getRequestedBy());
        assertEquals(ProjectGenerationStatus.DRAFT_READY, stored.getStatus());
        assertEquals(ProjectGenerationMode.NEW_PROJECT, stored.getMode());
        assertNotNull(stored.getDraftJson());
        assertEquals(null, stored.getProject());
        verify(aiPlanningClient).generatePlan(stored.getId(), stored.getPrompt());
    }

    @Test
    void generateDraftPersistsFailedStateWhenProviderFails() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(aiPlanningClient.generatePlan(any(UUID.class), any(String.class)))
                .thenThrow(new AIPlanningUnavailableException("offline"));

        assertThrows(
                AIPlanningUnavailableException.class,
                () -> service.generateDraft("alice", "Build a useful household budget application")
        );

        ArgumentCaptor<ProjectGenerationRun> runCaptor = ArgumentCaptor.forClass(ProjectGenerationRun.class);
        verify(runRepository, org.mockito.Mockito.times(2)).save(runCaptor.capture());
        assertEquals(ProjectGenerationStatus.FAILED, runCaptor.getValue().getStatus());
        assertEquals("AI_PLANNING_UNAVAILABLE", runCaptor.getValue().getErrorCode());
    }

    @Test
    void generateDraftDoesNotLeaveUnexpectedPlanningFailureProcessing() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(aiPlanningClient.generatePlan(any(UUID.class), any(String.class)))
                .thenThrow(new IllegalStateException("unexpected provider payload"));

        assertThrows(
                AIPlanningUnavailableException.class,
                () -> service.generateDraft("alice", "Build a useful household budget application")
        );

        ArgumentCaptor<ProjectGenerationRun> runCaptor = ArgumentCaptor.forClass(
                ProjectGenerationRun.class
        );
        verify(runRepository, org.mockito.Mockito.times(2)).save(runCaptor.capture());
        assertEquals(ProjectGenerationStatus.FAILED, runCaptor.getValue().getStatus());
        assertEquals("AI_PLANNING_FAILED", runCaptor.getValue().getErrorCode());
    }

    @Test
    void generateExistingTaskDraftPersistsAuthorizedTargetAndStructuredContext() {
        Project project = Project.builder().id(20L).name("Budget App").owner(owner).build();
        com.pablomarotta.smart_task_manager.model.Task target =
                com.pablomarotta.smart_task_manager.model.Task.builder()
                        .id(201L)
                        .project(project)
                        .title("Import bank transactions")
                        .build();
        AIPlanningContext context = new AIPlanningContext(
                ProjectGenerationMode.EXISTING_TASK,
                new PlanningProjectSnapshot(20L, "Budget App", "Manage household spending"),
                201L,
                List.<PlanningTaskSnapshot>of()
        );
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(contextService.capture(20L, 201L, "alice")).thenReturn(
                new ProjectPlanningContextService.CapturedContext(
                        project,
                        target,
                        context,
                        "a".repeat(64)
                )
        );
        when(aiPlanningClient.generatePlan(any(UUID.class), any(String.class), eq(context)))
                .thenAnswer(invocation -> PlanningTestFixtures.response(invocation.getArgument(0)));

        ProjectGenerationDraftResponse result = service.generateDraftForTask(
                "alice",
                20L,
                201L,
                "Break this ticket into implementation steps"
        );

        assertEquals(ProjectGenerationStatus.DRAFT_READY, result.status());
        ArgumentCaptor<ProjectGenerationRun> runCaptor = ArgumentCaptor.forClass(ProjectGenerationRun.class);
        verify(runRepository, org.mockito.Mockito.times(2)).save(runCaptor.capture());
        ProjectGenerationRun stored = runCaptor.getValue();
        assertEquals(ProjectGenerationMode.EXISTING_TASK, stored.getMode());
        assertEquals(project, stored.getProject());
        assertEquals(target, stored.getTargetTask());
        assertEquals("a".repeat(64), stored.getContextHash());
        verify(aiPlanningClient).generatePlan(stored.getId(), stored.getPrompt(), context);
    }

    @Test
    void retryUsesTheClaimedRunIdAndRefreshedContext() {
        Project project = Project.builder().id(20L).name("Budget App").owner(owner).build();
        var target = com.pablomarotta.smart_task_manager.model.Task.builder()
                .id(201L)
                .project(project)
                .title("Import bank transactions")
                .build();
        AIPlanningContext context = new AIPlanningContext(
                ProjectGenerationMode.EXISTING_TASK,
                new PlanningProjectSnapshot(20L, "Budget App", "Manage household spending"),
                201L,
                List.of()
        );
        ProjectGenerationRun failedRun = ProjectGenerationRun.builder()
                .id(UUID.randomUUID())
                .requestedBy(owner)
                .prompt("Break this ticket into implementation steps")
                .mode(ProjectGenerationMode.EXISTING_TASK)
                .status(ProjectGenerationStatus.PROCESSING)
                .project(project)
                .targetTask(target)
                .attemptCount(2)
                .build();
        when(retryClaimService.claim(failedRun.getId(), "alice")).thenReturn(
                new ProjectGenerationRunRetryClaimService.RetryClaim(failedRun, context)
        );
        when(aiPlanningClient.generatePlan(failedRun.getId(), failedRun.getPrompt(), context))
                .thenReturn(PlanningTestFixtures.response(failedRun.getId()));

        ProjectGenerationDraftResponse result = service.retryDraft(failedRun.getId(), "alice");

        assertEquals(failedRun.getId(), result.runId());
        assertEquals(ProjectGenerationStatus.DRAFT_READY, result.status());
        assertEquals(2, failedRun.getAttemptCount());
        verify(aiPlanningClient).generatePlan(failedRun.getId(), failedRun.getPrompt(), context);
    }

}
