package com.pablomarotta.smart_task_manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.PlanningTestFixtures;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationConfirmationResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectPlanDraft;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationRun;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectGenerationRunRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskAcceptanceCriterionRepository;
import com.pablomarotta.smart_task_manager.repository.TaskDependencyRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationConfirmationServiceTest {

    @Mock
    private ProjectGenerationRunRepository runRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskAcceptanceCriterionRepository criterionRepository;
    @Mock
    private TaskDependencyRepository dependencyRepository;

    private ProjectGenerationConfirmationService service;
    private ProjectGenerationRun run;

    @BeforeEach
    void setUp() throws Exception {
        service = new ProjectGenerationConfirmationService(
                runRepository,
                projectRepository,
                taskRepository,
                criterionRepository,
                dependencyRepository,
                new ObjectMapper().findAndRegisterModules()
        );
        User owner = User.builder().id(7L).username("alice").email("alice@example.com")
                .password("encoded").fullName("Alice").build();
        run = ProjectGenerationRun.builder()
                .id(UUID.randomUUID())
                .requestedBy(owner)
                .prompt("Build a useful household budget application")
                .status(ProjectGenerationStatus.DRAFT_READY)
                .draftJson(new ObjectMapper().writeValueAsString(PlanningTestFixtures.draft()))
                .build();
    }

    @Test
    void confirmAtomicallyCreatesProjectTicketsCriteriaAndDependencies() {
        when(runRepository.findLockedById(run.getId())).thenReturn(Optional.of(run));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(41L);
            return project;
        });
        AtomicLong taskId = new AtomicLong(100);
        when(taskRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Task> tasks = new ArrayList<>();
            invocation.<Iterable<Task>>getArgument(0).forEach(task -> {
                task.setId(taskId.getAndIncrement());
                tasks.add(task);
            });
            return tasks;
        });

        ProjectGenerationConfirmationResponse result = service.confirm(
                run.getId(),
                "alice",
                PlanningTestFixtures.draft()
        );

        assertEquals(41L, result.projectId());
        assertEquals(List.of(100L, 101L, 102L), result.taskIds());
        assertEquals(false, result.alreadyConfirmed());
        assertEquals(ProjectGenerationStatus.CONFIRMED, run.getStatus());
        assertEquals(41L, run.getProject().getId());
        verify(criterionRepository).saveAll(org.mockito.ArgumentMatchers.argThat(
                values -> ((List<?>) values).size() == 6
        ));
        verify(dependencyRepository).saveAll(org.mockito.ArgumentMatchers.argThat(
                values -> ((List<?>) values).size() == 2
        ));
        verify(runRepository).save(run);
    }

    @Test
    void repeatedConfirmationReturnsExistingProjectWithoutDuplicatingWrites() {
        Project project = Project.builder().id(41L).name("Budget App").owner(run.getRequestedBy()).build();
        run.setStatus(ProjectGenerationStatus.CONFIRMED);
        run.setProject(project);
        when(runRepository.findLockedById(run.getId())).thenReturn(Optional.of(run));
        when(taskRepository.findByGenerationRunIdOrderByPositionAsc(run.getId())).thenReturn(List.of(
                Task.builder().id(100L).project(project).title("One").build(),
                Task.builder().id(101L).project(project).title("Two").build()
        ));

        ProjectGenerationConfirmationResponse result = service.confirm(
                run.getId(), "alice", PlanningTestFixtures.draft()
        );

        assertEquals(true, result.alreadyConfirmed());
        assertEquals(List.of(100L, 101L), result.taskIds());
        verify(projectRepository, never()).save(any());
        verify(taskRepository, never()).saveAll(any());
    }

    @Test
    void onlyRunOwnerCanConfirm() {
        when(runRepository.findLockedById(run.getId())).thenReturn(Optional.of(run));

        assertThrows(
                AccessDeniedException.class,
                () -> service.confirm(run.getId(), "mallory", PlanningTestFixtures.draft())
        );

        verify(projectRepository, never()).save(any());
    }

    @Test
    void invalidDependencyIsRejectedBeforeAnyWrite() {
        when(runRepository.findLockedById(run.getId())).thenReturn(Optional.of(run));
        ProjectPlanDraft original = PlanningTestFixtures.draft();
        var first = original.tickets().getFirst();
        var invalidFirst = new com.pablomarotta.smart_task_manager.dto.planning.PlanningTicketDraft(
                first.clientId(), first.title(), first.description(), first.priority(),
                first.estimatedHours(), first.acceptanceCriteria(), List.of("missing"),
                first.category(), first.dueInDays()
        );
        ProjectPlanDraft invalid = new ProjectPlanDraft(
                original.name(), original.objective(), original.assumptions(), original.risks(),
                List.of(invalidFirst, original.tickets().get(1), original.tickets().get(2))
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.confirm(run.getId(), "alice", invalid)
        );

        verify(projectRepository, never()).save(any());
    }

    @Test
    void failedTaskPersistenceDoesNotMarkRunConfirmed() {
        when(runRepository.findLockedById(run.getId())).thenReturn(Optional.of(run));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.saveAll(any())).thenThrow(new IllegalStateException("database failure"));

        assertThrows(
                IllegalStateException.class,
                () -> service.confirm(run.getId(), "alice", PlanningTestFixtures.draft())
        );

        assertEquals(ProjectGenerationStatus.DRAFT_READY, run.getStatus());
        assertEquals(null, run.getProject());
        verify(runRepository, never()).save(run);
    }
}
