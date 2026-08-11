package com.pablomarotta.smart_task_manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.PlanningTestFixtures;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationRunDetailResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationRunSummaryResponse;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationRunQueryServiceTest {

    @Mock
    private ProjectGenerationRunRepository runRepository;

    private ProjectGenerationRunQueryService service;
    private ObjectMapper objectMapper;
    private ProjectGenerationRun run;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new ProjectGenerationRunQueryService(runRepository, objectMapper);
        User owner = User.builder()
                .id(7L)
                .username("alice")
                .email("alice@example.com")
                .password("encoded")
                .build();
        Project project = Project.builder()
                .id(20L)
                .name("Budget App")
                .owner(owner)
                .build();
        Task target = Task.builder()
                .id(201L)
                .project(project)
                .title("Import bank transactions")
                .build();
        run = ProjectGenerationRun.builder()
                .id(UUID.randomUUID())
                .requestedBy(owner)
                .prompt("Break this ticket into implementation steps")
                .mode(ProjectGenerationMode.EXISTING_TASK)
                .status(ProjectGenerationStatus.FAILED)
                .project(project)
                .targetTask(target)
                .draftJson(objectMapper.writeValueAsString(PlanningTestFixtures.draft()))
                .qualityJson(objectMapper.writeValueAsString(
                        PlanningTestFixtures.response(UUID.randomUUID()).quality()
                ))
                .revisionCount(1)
                .modelName("gemma3:4b")
                .errorCode("AI_PLANNING_UNAVAILABLE")
                .attemptCount(2)
                .createdAt(LocalDateTime.of(2026, 8, 11, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 8, 11, 10, 5))
                .build();
    }

    @Test
    void listsOnlyRepositoryScopedRecentRunsWithSafeRecoveryMetadata() {
        when(runRepository.findTop10ByRequestedByUsernameOrderByUpdatedAtDesc("alice"))
                .thenReturn(List.of(run));

        List<ProjectGenerationRunSummaryResponse> result = service.listRecent("alice");

        assertEquals(1, result.size());
        ProjectGenerationRunSummaryResponse summary = result.getFirst();
        assertEquals(run.getId(), summary.runId());
        assertEquals(ProjectGenerationMode.EXISTING_TASK, summary.mode());
        assertEquals(ProjectGenerationStatus.FAILED, summary.status());
        assertEquals("Budget App", summary.projectName());
        assertEquals(201L, summary.targetTaskId());
        assertEquals("Import bank transactions", summary.targetTaskTitle());
        assertEquals(2, summary.attemptCount());
        assertEquals("AI_PLANNING_UNAVAILABLE", summary.errorCode());
        assertTrue(summary.retryable());
    }

    @Test
    void restoresThePersistedDraftAndQualityForItsRequester() {
        run.setStatus(ProjectGenerationStatus.DRAFT_READY);
        run.setErrorCode(null);
        when(runRepository.findByIdAndRequestedByUsername(run.getId(), "alice"))
                .thenReturn(Optional.of(run));

        ProjectGenerationRunDetailResponse result = service.get(run.getId(), "alice");

        assertEquals(run.getId(), result.runId());
        assertEquals(ProjectGenerationStatus.DRAFT_READY, result.status());
        assertEquals("Budget App", result.draft().name());
        assertEquals(100, result.quality().score());
        assertEquals(1, result.revisionCount());
        assertEquals("gemma3:4b", result.model());
        assertEquals("Budget App", result.projectName());
        assertEquals("Import bank transactions", result.targetTaskTitle());
    }

    @Test
    void hidesAnotherUsersRunAsNotFound() {
        when(runRepository.findByIdAndRequestedByUsername(run.getId(), "mallory"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.get(run.getId(), "mallory")
        );

        assertEquals(404, exception.getStatusCode().value());
    }
}
