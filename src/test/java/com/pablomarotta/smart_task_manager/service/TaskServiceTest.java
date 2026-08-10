package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.TaskResponse;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

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
        Project project = Project.builder().id(20L).name("Job Application Tracker").build();
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

        when(taskRepository.findByProjectIdOrderByPositionAsc(20L))
                .thenReturn(List.of(discoverJobs, trackApplications));
        when(acceptanceCriterionRepository.findByProjectId(20L))
                .thenReturn(List.of(firstCriterion, secondCriterion));
        when(dependencyRepository.findByProjectId(20L)).thenReturn(List.of(dependency));

        List<TaskResponse> responses = taskService.getTasksByProjectId(20L);

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
