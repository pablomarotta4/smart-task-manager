package com.pablomarotta.smart_task_manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.model.TaskAcceptanceCriterion;
import com.pablomarotta.smart_task_manager.model.TaskDependency;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskAcceptanceCriterionRepository;
import com.pablomarotta.smart_task_manager.repository.TaskDependencyRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectPlanningContextServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskAcceptanceCriterionRepository criterionRepository;
    @Mock
    private TaskDependencyRepository dependencyRepository;

    private ProjectPlanningContextService service;
    private Project project;
    private Task selected;
    private Task existing;

    @BeforeEach
    void setUp() {
        service = new ProjectPlanningContextService(
                projectRepository,
                taskRepository,
                criterionRepository,
                dependencyRepository,
                new ObjectMapper().findAndRegisterModules()
        );
        User owner = User.builder().id(7L).username("alice").active(true).build();
        project = Project.builder()
                .id(20L)
                .name("Job Application Tracker")
                .objective("Track opportunities through offer decisions.")
                .owner(owner)
                .build();
        selected = Task.builder()
                .id(201L)
                .project(project)
                .title("Capture opportunity")
                .description("Record company, role, source, and application link.")
                .status(Status.TODO)
                .priority(Priority.HIGH)
                .position(0)
                .build();
        existing = Task.builder()
                .id(202L)
                .project(project)
                .title("Track interview stages")
                .description("Show the current interview stage and next action.")
                .status(Status.IN_PROGRESS)
                .priority(Priority.MEDIUM)
                .position(1)
                .build();
    }

    @Test
    void capturesAuthorizedSelectedTaskAndOrderedProjectContext() {
        TaskAcceptanceCriterion criterion = TaskAcceptanceCriterion.builder()
                .task(selected)
                .criterion("Every opportunity records its source")
                .position(0)
                .build();
        TaskDependency dependency = TaskDependency.builder()
                .task(existing)
                .dependsOnTask(selected)
                .build();
        stubProjectContext(List.of(criterion), List.of(dependency));

        ProjectPlanningContextService.CapturedContext captured = service.capture(
                20L,
                201L,
                "alice"
        );

        assertEquals(project, captured.project());
        assertEquals(selected, captured.targetTask());
        assertEquals(201L, captured.context().selectedTaskId());
        assertEquals(List.of(201L, 202L), captured.context().tasks().stream()
                .map(task -> task.id())
                .toList());
        assertEquals(
                List.of("Every opportunity records its source"),
                captured.context().tasks().getFirst().acceptanceCriteria()
        );
        assertEquals(List.of(201L), captured.context().tasks().getLast().dependsOnTaskIds());
        assertEquals(64, captured.contextHash().length());
    }

    @Test
    void contextHashChangesWhenTheBacklogChanges() {
        stubProjectContext(List.of(), List.of());

        String firstHash = service.capture(20L, 201L, "alice").contextHash();
        existing.setTitle("Track every interview and follow-up");
        String changedHash = service.capture(20L, 201L, "alice").contextHash();

        assertNotEquals(firstHash, changedHash);
    }

    @Test
    void rejectsForeignProjectBeforeLoadingItsTasks() {
        when(projectRepository.findByIdAndOwnerUsername(20L, "mallory"))
                .thenReturn(Optional.empty());

        assertThrows(
                ProjectNotFoundException.class,
                () -> service.capture(20L, 201L, "mallory")
        );

        verify(taskRepository, never()).findByProjectIdOrderByPositionAsc(20L);
    }

    private void stubProjectContext(
            List<TaskAcceptanceCriterion> criteria,
            List<TaskDependency> dependencies
    ) {
        when(projectRepository.findByIdAndOwnerUsername(20L, "alice"))
                .thenReturn(Optional.of(project));
        when(taskRepository.findByProjectIdOrderByPositionAsc(20L))
                .thenReturn(List.of(selected, existing));
        when(criterionRepository.findByProjectId(20L)).thenReturn(criteria);
        when(dependencyRepository.findByProjectId(20L)).thenReturn(dependencies);
    }
}
