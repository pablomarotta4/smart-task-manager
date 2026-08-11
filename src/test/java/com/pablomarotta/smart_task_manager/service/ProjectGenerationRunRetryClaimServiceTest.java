package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningContext;
import com.pablomarotta.smart_task_manager.dto.planning.PlanningProjectSnapshot;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationRun;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectGenerationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationRunRetryClaimServiceTest {

    @Mock
    private ProjectGenerationRunRepository runRepository;
    @Mock
    private ProjectPlanningContextService contextService;

    private ProjectGenerationRunRetryClaimService service;
    private ProjectGenerationRun run;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new ProjectGenerationRunRetryClaimService(runRepository, contextService);
        owner = User.builder()
                .id(7L)
                .username("alice")
                .email("alice@example.com")
                .password("encoded")
                .build();
        run = ProjectGenerationRun.builder()
                .id(UUID.randomUUID())
                .requestedBy(owner)
                .prompt("Build a useful household budget application")
                .mode(ProjectGenerationMode.NEW_PROJECT)
                .status(ProjectGenerationStatus.FAILED)
                .errorCode("AI_PLANNING_UNAVAILABLE")
                .draftJson("stale-draft")
                .qualityJson("stale-quality")
                .revisionCount(1)
                .modelName("old-model")
                .attemptCount(1)
                .updatedAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }

    @Test
    void claimsFailedRunAndClearsItsPreviousAttemptPayload() {
        when(runRepository.findLockedById(run.getId())).thenReturn(Optional.of(run));

        ProjectGenerationRunRetryClaimService.RetryClaim claim = service.claim(
                run.getId(),
                "alice"
        );

        assertEquals(run, claim.run());
        assertNull(claim.context());
        assertEquals(ProjectGenerationStatus.PROCESSING, run.getStatus());
        assertEquals(2, run.getAttemptCount());
        assertNull(run.getErrorCode());
        assertNull(run.getDraftJson());
        assertNull(run.getQualityJson());
        assertNull(run.getRevisionCount());
        assertNull(run.getModelName());
        verify(runRepository).save(run);
    }

    @Test
    void refreshesExistingTaskContextBeforeRetrying() {
        Project project = Project.builder().id(20L).name("Budget App").owner(owner).build();
        Task target = Task.builder().id(201L).project(project).title("Import data").build();
        AIPlanningContext context = new AIPlanningContext(
                ProjectGenerationMode.EXISTING_TASK,
                new PlanningProjectSnapshot(20L, "Budget App", "Manage spending"),
                201L,
                List.of()
        );
        run.setMode(ProjectGenerationMode.EXISTING_TASK);
        run.setProject(project);
        run.setTargetTask(target);
        run.setContextHash("a".repeat(64));
        when(runRepository.findLockedById(run.getId())).thenReturn(Optional.of(run));
        when(contextService.capture(20L, 201L, "alice")).thenReturn(
                new ProjectPlanningContextService.CapturedContext(
                        project,
                        target,
                        context,
                        "b".repeat(64)
                )
        );

        ProjectGenerationRunRetryClaimService.RetryClaim claim = service.claim(
                run.getId(),
                "alice"
        );

        assertEquals(context, claim.context());
        assertEquals("b".repeat(64), run.getContextHash());
    }

    @Test
    void allowsAnAbandonedProcessingRunButRejectsAnActiveOne() {
        run.setStatus(ProjectGenerationStatus.PROCESSING);
        run.setUpdatedAt(LocalDateTime.now().minusMinutes(3));
        when(runRepository.findLockedById(run.getId())).thenReturn(Optional.of(run));

        ProjectGenerationRunRetryClaimService.RetryClaim claim = service.claim(
                run.getId(),
                "alice"
        );

        assertTrue(claim.run().getAttemptCount() > 1);

        run.setStatus(ProjectGenerationStatus.PROCESSING);
        run.setUpdatedAt(LocalDateTime.now());
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.claim(run.getId(), "alice")
        );
        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void rejectsAnotherUserAndACompletedDraft() {
        when(runRepository.findLockedById(run.getId())).thenReturn(Optional.of(run));
        assertThrows(AccessDeniedException.class, () -> service.claim(run.getId(), "mallory"));
        verify(runRepository, never()).save(run);

        run.setStatus(ProjectGenerationStatus.DRAFT_READY);
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.claim(run.getId(), "alice")
        );
        assertEquals(409, exception.getStatusCode().value());
    }
}
