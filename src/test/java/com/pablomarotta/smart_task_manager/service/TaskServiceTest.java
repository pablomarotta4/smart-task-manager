package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.TaskRequest;
import com.pablomarotta.smart_task_manager.dto.TaskResponse;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.exception.TaskNotFoundException;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.model.TaskAcceptanceCriterion;
import com.pablomarotta.smart_task_manager.model.TaskDependency;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskAcceptanceCriterionRepository;
import com.pablomarotta.smart_task_manager.repository.TaskDependencyRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final String OWNER_USERNAME = "alice";

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AIService aiService;

    @Mock
    private TaskAcceptanceCriterionRepository acceptanceCriterionRepository;

    @Mock
    private TaskDependencyRepository dependencyRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void getTasksByProjectIdReturnsOrderedPlanningDetails() {
        Project project = ownedProject();
        Task discoverJobs = task(101L, project, "discover-jobs", "Discover target jobs", 0);
        Task trackApplications = task(102L, project, "track-applications", "Track applications", 1);

        TaskAcceptanceCriterion firstCriterion = TaskAcceptanceCriterion.builder()
                .task(discoverJobs)
                .criterion("A saved opportunity includes company, role, and source")
                .position(0)
                .build();
        TaskAcceptanceCriterion secondCriterion = TaskAcceptanceCriterion.builder()
                .task(trackApplications)
                .criterion("Every application has a visible current stage")
                .position(0)
                .build();
        TaskDependency dependency = TaskDependency.builder()
                .task(trackApplications)
                .dependsOnTask(discoverJobs)
                .build();

        when(projectRepository.findByIdAndOwnerUsername(20L, OWNER_USERNAME))
                .thenReturn(Optional.of(project));
        when(taskRepository.findByProjectIdOrderByPositionAsc(20L))
                .thenReturn(List.of(discoverJobs, trackApplications));
        when(acceptanceCriterionRepository.findByProjectId(20L))
                .thenReturn(List.of(firstCriterion, secondCriterion));
        when(dependencyRepository.findByProjectId(20L)).thenReturn(List.of(dependency));

        List<TaskResponse> responses = taskService.getTasksByProjectId(20L, OWNER_USERNAME);

        assertEquals(List.of(101L, 102L), responses.stream().map(TaskResponse::getId).toList());
        assertEquals(new BigDecimal("4.50"), responses.get(0).getEstimatedHours());
        assertEquals("Collect relevant roles from selected sources.", responses.get(0).getAiSummary());
        assertEquals(
                List.of("A saved opportunity includes company, role, and source"),
                responses.get(0).getAcceptanceCriteria()
        );
        assertEquals(List.of("discover-jobs"), responses.get(1).getDependsOn());
        verify(taskRepository).findByProjectIdOrderByPositionAsc(20L);
        verify(acceptanceCriterionRepository).findByProjectId(20L);
        verify(dependencyRepository).findByProjectId(20L);
    }

    @Test
    void getTasksByProjectIdRejectsForeignProjectBeforeLoadingBacklog() {
        when(projectRepository.findByIdAndOwnerUsername(20L, OWNER_USERNAME))
                .thenReturn(Optional.empty());

        assertThrows(
                ProjectNotFoundException.class,
                () -> taskService.getTasksByProjectId(20L, OWNER_USERNAME)
        );

        verify(taskRepository, never()).findByProjectIdOrderByPositionAsc(any());
        verify(acceptanceCriterionRepository, never()).findByProjectId(any());
        verify(dependencyRepository, never()).findByProjectId(any());
    }

    @Test
    void createTaskRejectsForeignProjectBeforeCallingAi() {
        TaskRequest request = taskRequest();
        when(projectRepository.findByIdAndOwnerUsername(20L, OWNER_USERNAME))
                .thenReturn(Optional.empty());

        assertThrows(
                ProjectNotFoundException.class,
                () -> taskService.createTask(request, OWNER_USERNAME)
        );

        verify(aiService, never()).classifyTask(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createTaskPersistsManualFieldsWithoutCallingAi() {
        Project project = ownedProject();
        TaskRequest request = taskRequest();
        request.setPosition(null);
        when(projectRepository.findByIdAndOwnerUsername(20L, OWNER_USERNAME))
                .thenReturn(Optional.of(project));
        when(taskRepository.countByProjectId(20L)).thenReturn(2L);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task saved = invocation.getArgument(0);
            saved.setId(103L);
            return saved;
        });

        TaskResponse response = taskService.createTask(request, OWNER_USERNAME);

        assertEquals(103L, response.getId());
        assertEquals(2, response.getPosition());
        verify(taskRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getCreatedBy() == project.getOwner()
                        && saved.getPosition() == 2
                        && saved.getAiSummary() == null
                        && saved.getAiPriority() == null
        ));
        verifyNoInteractions(aiService);
    }

    @Test
    void updateTaskRejectsForeignTask() {
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.empty());

        assertThrows(
                TaskNotFoundException.class,
                () -> taskService.updateTask(101L, taskRequest(), OWNER_USERNAME)
        );

        verify(taskRepository, never()).save(any());
    }

    @Test
    void updateTaskClearsOptionalFieldsAndCompletionTimestamp() {
        Project project = ownedProject();
        Task existing = task(101L, project, null, "Existing ticket", 1);
        existing.setDescription("Old description");
        existing.setCategory("Old category");
        existing.setStatus(Status.DONE);
        existing.setCompletedAt(LocalDateTime.now().minusDays(1));
        TaskRequest request = taskRequest();
        request.setDescription(null);
        request.setCategory(null);
        request.setDueDate(null);
        request.setStatus(Status.TODO);
        request.setPosition(1);
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.of(existing));
        when(taskRepository.save(existing)).thenReturn(existing);

        TaskResponse response = taskService.updateTask(101L, request, OWNER_USERNAME);

        assertNull(response.getDescription());
        assertNull(response.getCategory());
        assertNull(response.getDueDate());
        assertNull(existing.getCompletedAt());
    }

    @Test
    void updateTaskMarksCompletionWhenEnteringDone() {
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        TaskRequest request = taskRequest();
        request.setStatus(Status.DONE);
        request.setPosition(1);
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.of(existing));
        when(taskRepository.save(existing)).thenReturn(existing);

        taskService.updateTask(101L, request, OWNER_USERNAME);

        assertNotNull(existing.getCompletedAt());
    }

    @Test
    void updateTaskRejectsMovingTaskToAnotherProject() {
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        TaskRequest request = taskRequest();
        request.setProjectId(21L);
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.of(existing));

        assertThrows(
                IllegalArgumentException.class,
                () -> taskService.updateTask(101L, request, OWNER_USERNAME)
        );

        verify(taskRepository, never()).save(any());
    }

    @Test
    void deleteTaskRejectsForeignTask() {
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.empty());

        assertThrows(
                TaskNotFoundException.class,
                () -> taskService.deleteTask(101L, OWNER_USERNAME)
        );

        verify(taskRepository, never()).delete(any());
    }

    @Test
    void deleteTaskRemovesOwnedTask() {
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.of(existing));

        taskService.deleteTask(101L, OWNER_USERNAME);

        verify(taskRepository).delete(existing);
    }

    @Test
    void statusMutationRejectsForeignTask() {
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.empty());

        assertThrows(
                TaskNotFoundException.class,
                () -> taskService.updateTaskStatus(101L, Status.DONE, OWNER_USERNAME)
        );

        verify(taskRepository, never()).save(any());
    }

    @Test
    void statusMutationClearsCompletionWhenReopeningTask() {
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        existing.setStatus(Status.DONE);
        existing.setCompletedAt(LocalDateTime.now().minusHours(1));
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.of(existing));
        when(taskRepository.save(existing)).thenReturn(existing);

        taskService.updateTaskStatus(101L, Status.IN_PROGRESS, OWNER_USERNAME);

        assertNull(existing.getCompletedAt());
    }

    @Test
    void assignmentRejectsForeignTask() {
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.empty());

        assertThrows(
                TaskNotFoundException.class,
                () -> taskService.assignTask(101L, 2L, OWNER_USERNAME)
        );

        verify(userRepository, never()).findById(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void priorityMutationRejectsForeignTask() {
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.empty());

        assertThrows(
                TaskNotFoundException.class,
                () -> taskService.updateTaskPriority(101L, Priority.URGENT, OWNER_USERNAME)
        );

        verify(taskRepository, never()).save(any());
    }

    @Test
    void todoQueryUsesOwnerScopedRepositoryMethod() {
        when(taskRepository.findByStatusAndProjectOwnerUsername(Status.TODO, OWNER_USERNAME))
                .thenReturn(List.of());

        assertEquals(List.of(), taskService.getTodoTasks(OWNER_USERNAME));

        verify(taskRepository).findByStatusAndProjectOwnerUsername(Status.TODO, OWNER_USERNAME);
        verify(taskRepository, never()).findByStatus(Status.TODO);
    }

    private Project ownedProject() {
        User owner = User.builder().id(1L).username(OWNER_USERNAME).build();
        return Project.builder()
                .id(20L)
                .name("Job Application Tracker")
                .owner(owner)
                .build();
    }

    private TaskRequest taskRequest() {
        TaskRequest request = new TaskRequest();
        request.setTitle("Create opportunity intake");
        request.setDescription("Capture the company, role, source, and application link.");
        request.setStatus(Status.TODO);
        request.setProjectId(20L);
        request.setPriority(Priority.HIGH);
        request.setPosition(0);
        return request;
    }

    private Task task(Long id, Project project, String clientId, String title, int position) {
        return Task.builder()
                .id(id)
                .project(project)
                .planningClientId(clientId)
                .title(title)
                .description("Detailed delivery notes for " + title.toLowerCase())
                .status(Status.TODO)
                .position(position)
                .priority(Priority.HIGH)
                .category("Planning")
                .dueDate(LocalDate.of(2026, 8, 20))
                .estimatedHours(new BigDecimal("4.50"))
                .aiSummary("Collect relevant roles from selected sources.")
                .build();
    }
}
