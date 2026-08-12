package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.TaskRequest;
import com.pablomarotta.smart_task_manager.dto.TaskResponse;
import com.pablomarotta.smart_task_manager.dto.ProjectMemberUserResponse;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.exception.TaskNotFoundException;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectMembership;
import com.pablomarotta.smart_task_manager.model.ProjectRole;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.model.TaskAcceptanceCriterion;
import com.pablomarotta.smart_task_manager.model.TaskDependency;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectMembershipRepository;
import com.pablomarotta.smart_task_manager.repository.TaskAcceptanceCriterionRepository;
import com.pablomarotta.smart_task_manager.repository.TaskDependencyRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
    private ProjectMembershipRepository membershipRepository;

    @Mock
    private AIService aiService;

    @Mock
    private TaskAcceptanceCriterionRepository acceptanceCriterionRepository;

    @Mock
    private TaskDependencyRepository dependencyRepository;

    @Mock
    private ProjectAccessPolicy accessPolicy;

    @InjectMocks
    private TaskService taskService;

    @BeforeEach
    void configureAccessPolicy() {
        lenient().when(accessPolicy.requireMember(anyLong(), anyString()))
                .thenAnswer(invocation -> membership(invocation.getArgument(1), ProjectRole.MEMBER));
        lenient().when(accessPolicy.requireManager(anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    String username = invocation.getArgument(1);
                    if (!OWNER_USERNAME.equals(username)) {
                        throw new org.springframework.security.access.AccessDeniedException(
                                "Project manager permission is required"
                        );
                    }
                    return membership(username, ProjectRole.OWNER);
                });
        lenient().when(accessPolicy.requireTaskViewer(anyLong(), anyString()))
                .thenAnswer(invocation -> findTaskFor(invocation.getArgument(0), invocation.getArgument(1)));
        lenient().when(accessPolicy.requireTaskEditor(anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    Long taskId = invocation.getArgument(0);
                    String username = invocation.getArgument(1);
                    Task task = findTaskFor(taskId, username);
                    ProjectMembership membership = membership(
                            username,
                            OWNER_USERNAME.equals(username) ? ProjectRole.OWNER : ProjectRole.MEMBER
                    );
                    if (membership.getRole() == ProjectRole.MEMBER
                            && (task.getAssignee() == null || !username.equals(task.getAssignee().getUsername()))) {
                        throw new org.springframework.security.access.AccessDeniedException(
                                "Members can edit only their assigned tasks"
                        );
                    }
                    return new ProjectAccessPolicy.TaskAccess(task, membership);
                });
    }

    @Test
    void getTasksByProjectIdReturnsOrderedPlanningDetails() {
        Project project = ownedProject();
        Task discoverJobs = task(101L, project, "discover-jobs", "Discover target jobs", 0);
        Task trackApplications = task(102L, project, "track-applications", "Track applications", 1);
        trackApplications.setParentTask(discoverJobs);

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

        List<TaskResponse> responses = taskService.getTasksByProjectId(20L, OWNER_USERNAME);

        assertEquals(List.of(101L, 102L), responses.stream().map(TaskResponse::getId).toList());
        assertEquals(new BigDecimal("4.50"), responses.get(0).getEstimatedHours());
        assertEquals("Collect relevant roles from selected sources.", responses.get(0).getAiSummary());
        assertEquals(
                List.of("A saved opportunity includes company, role, and source"),
                responses.get(0).getAcceptanceCriteria()
        );
        assertEquals(List.of("discover-jobs"), responses.get(1).getDependsOn());
        assertEquals(101L, responses.get(1).getParentTaskId());
        verify(taskRepository).findByProjectIdOrderByPositionAsc(20L);
        verify(acceptanceCriterionRepository).findByProjectId(20L);
        verify(dependencyRepository).findByProjectId(20L);
    }

    @Test
    void getTasksByProjectIdRejectsForeignProjectBeforeLoadingBacklog() {
        when(accessPolicy.requireMember(20L, OWNER_USERNAME))
                .thenThrow(new ProjectNotFoundException("Project not found with id: 20"));

        assertThrows(
                ProjectNotFoundException.class,
                () -> taskService.getTasksByProjectId(20L, OWNER_USERNAME)
        );

        verify(taskRepository, never()).findByProjectIdOrderByPositionAsc(any());
        verify(acceptanceCriterionRepository, never()).findByProjectId(any());
        verify(dependencyRepository, never()).findByProjectId(any());
    }

    @Test
    void getMyWorkReturnsAssignedTasksAcrossProjectsWithPlanningDetails() {
        User member = User.builder().id(2L).username("bob").active(true).build();
        Project firstProject = ownedProject();
        Project secondProject = Project.builder()
                .id(21L)
                .name("Launch plan")
                .owner(User.builder().id(3L).username("carol").build())
                .build();
        Task first = task(101L, firstProject, "discover-jobs", "Discover target jobs", 0);
        Task second = task(202L, secondProject, "publish-release", "Publish release", 1);
        first.setAssignee(member);
        second.setAssignee(member);
        TaskAcceptanceCriterion criterion = TaskAcceptanceCriterion.builder()
                .task(second)
                .criterion("Release notes are published")
                .position(0)
                .build();
        TaskDependency dependency = TaskDependency.builder()
                .task(second)
                .dependsOnTask(first)
                .build();
        when(taskRepository.findByAssigneeUsernameOrderByDueDateAscPositionAsc("bob"))
                .thenReturn(List.of(first, second));
        when(acceptanceCriterionRepository.findByTaskAssigneeUsername("bob"))
                .thenReturn(List.of(criterion));
        when(dependencyRepository.findByTaskAssigneeUsername("bob"))
                .thenReturn(List.of(dependency));

        List<TaskResponse> response = taskService.getMyWork("bob");

        assertEquals(List.of(101L, 202L), response.stream().map(TaskResponse::getId).toList());
        assertEquals("Job Application Tracker", response.getFirst().getProjectName());
        assertEquals("Launch plan", response.getLast().getProjectName());
        assertEquals(new BigDecimal("4.50"), response.getLast().getEstimatedHours());
        assertEquals(List.of("Release notes are published"), response.getLast().getAcceptanceCriteria());
        assertEquals(List.of("discover-jobs"), response.getLast().getDependsOn());
    }

    @Test
    void createTaskRejectsForeignProjectBeforeCallingAi() {
        TaskRequest request = taskRequest();
        when(accessPolicy.requireManager(20L, OWNER_USERNAME))
                .thenThrow(new ProjectNotFoundException("Project not found with id: 20"));

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
                OWNER_USERNAME.equals(saved.getCreatedBy().getUsername())
                        && saved.getPosition() == 2
                        && saved.getAiSummary() == null
                        && saved.getAiPriority() == null
        ));
        verifyNoInteractions(aiService);
    }

    @Test
    void createTaskRejectsAssigneeOutsideProject() {
        Project project = ownedProject();
        User outsider = User.builder().id(2L).username("mallory").active(true).build();
        TaskRequest request = taskRequest();
        request.setAssigneeId(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(outsider));
        when(membershipRepository.existsByProjectIdAndUserId(20L, 2L)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> taskService.createTask(request, OWNER_USERNAME)
        );

        verify(taskRepository, never()).save(any());
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
    void assignedContributorCannotDeleteTheirTask() {
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, "bob"))
                .thenReturn(Optional.empty());

        assertThrows(
                TaskNotFoundException.class,
                () -> taskService.deleteTask(101L, "bob")
        );

        verify(taskRepository, never()).delete(any());
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
    void assignedContributorCanUpdateTheirTaskStatus() {
        User member = User.builder().id(2L).username("bob").active(true).build();
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        existing.setAssignee(member);
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, "bob")).thenReturn(Optional.empty());
        when(taskRepository.findByIdAndAssigneeUsername(101L, "bob")).thenReturn(Optional.of(existing));
        when(taskRepository.save(existing)).thenReturn(existing);

        TaskResponse response = taskService.updateTaskStatus(101L, Status.DONE, "bob");

        assertEquals(Status.DONE, response.getStatus());
        assertNotNull(existing.getCompletedAt());
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
    void assignmentRejectsUserOutsideProject() {
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        User outsider = User.builder().id(2L).username("mallory").active(true).build();
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.of(existing));
        when(userRepository.findById(2L)).thenReturn(Optional.of(outsider));
        when(membershipRepository.existsByProjectIdAndUserId(20L, 2L)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> taskService.assignTask(101L, 2L, OWNER_USERNAME)
        );

        verify(taskRepository, never()).save(any());
    }

    @Test
    void assignmentAcceptsProjectMember() {
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        User member = User.builder().id(2L).username("bob").active(true).build();
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, OWNER_USERNAME))
                .thenReturn(Optional.of(existing));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(membershipRepository.existsByProjectIdAndUserId(20L, 2L)).thenReturn(true);
        when(taskRepository.save(existing)).thenReturn(existing);

        TaskResponse response = taskService.assignTask(101L, 2L, OWNER_USERNAME);

        assertEquals(2L, response.getAssigneeId());
        assertSame(member, existing.getAssignee());
    }

    @Test
    void assignedContributorCanUpdateTheirTicketWithoutReassigningIt() {
        User member = User.builder().id(2L).username("bob").active(true).build();
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        existing.setAssignee(member);
        TaskRequest request = taskRequest();
        request.setAssigneeId(2L);
        request.setTitle("Contributor update");
        request.setPosition(1);
        request.setDueDate(existing.getDueDate());
        request.setCategory(existing.getCategory());
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, "bob")).thenReturn(Optional.empty());
        when(taskRepository.findByIdAndAssigneeUsername(101L, "bob")).thenReturn(Optional.of(existing));
        when(taskRepository.save(existing)).thenReturn(existing);

        TaskResponse response = taskService.updateTask(101L, request, "bob");

        assertEquals("Contributor update", response.getTitle());
        assertSame(member, existing.getAssignee());
    }

    @Test
    void assignedContributorCannotReassignTheirTicket() {
        User member = User.builder().id(2L).username("bob").active(true).build();
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        existing.setAssignee(member);
        TaskRequest request = taskRequest();
        request.setAssigneeId(3L);
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, "bob")).thenReturn(Optional.empty());
        when(taskRepository.findByIdAndAssigneeUsername(101L, "bob")).thenReturn(Optional.of(existing));

        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> taskService.updateTask(101L, request, "bob")
        );

        verify(taskRepository, never()).save(any());
    }

    @Test
    void assignedContributorCannotChangePriorityThroughGeneralUpdate() {
        User member = User.builder().id(2L).username("bob").active(true).build();
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        existing.setAssignee(member);
        existing.setPriority(Priority.MEDIUM);
        TaskRequest request = taskRequest();
        request.setAssigneeId(2L);
        request.setPriority(Priority.URGENT);
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, "bob")).thenReturn(Optional.empty());
        when(taskRepository.findByIdAndAssigneeUsername(101L, "bob")).thenReturn(Optional.of(existing));

        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> taskService.updateTask(101L, request, "bob")
        );

        verify(taskRepository, never()).save(any());
    }

    @Test
    void assignedContributorCannotChangeDueDateThroughWholeTaskUpdate() {
        User member = User.builder().id(2L).username("bob").active(true).build();
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        existing.setAssignee(member);
        LocalDate originalDueDate = existing.getDueDate();
        String originalCategory = existing.getCategory();
        TaskRequest request = taskRequest();
        request.setAssigneeId(member.getId());
        request.setPriority(existing.getPriority());
        request.setPosition(existing.getPosition());
        request.setDueDate(originalDueDate.plusDays(1));
        request.setCategory(originalCategory);
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, "bob")).thenReturn(Optional.empty());
        when(taskRepository.findByIdAndAssigneeUsername(101L, "bob")).thenReturn(Optional.of(existing));

        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> taskService.updateTask(101L, request, "bob")
        );

        assertEquals(originalDueDate, existing.getDueDate());
        assertEquals(originalCategory, existing.getCategory());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void assignedContributorCannotChangeCategoryThroughWholeTaskUpdate() {
        User member = User.builder().id(2L).username("bob").active(true).build();
        Task existing = task(101L, ownedProject(), null, "Existing ticket", 1);
        existing.setAssignee(member);
        LocalDate originalDueDate = existing.getDueDate();
        String originalCategory = existing.getCategory();
        TaskRequest request = taskRequest();
        request.setAssigneeId(member.getId());
        request.setPriority(existing.getPriority());
        request.setPosition(existing.getPosition());
        request.setDueDate(originalDueDate);
        request.setCategory("Different category");
        when(taskRepository.findByIdAndProjectOwnerUsername(101L, "bob")).thenReturn(Optional.empty());
        when(taskRepository.findByIdAndAssigneeUsername(101L, "bob")).thenReturn(Optional.of(existing));

        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> taskService.updateTask(101L, request, "bob")
        );

        assertEquals(originalDueDate, existing.getDueDate());
        assertEquals(originalCategory, existing.getCategory());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void projectMemberDirectoryDoesNotExposeEmail() {
        User member = User.builder()
                .id(2L)
                .username("bob")
                .email("bob@example.com")
                .fullName("Bob Member")
                .active(true)
                .build();
        when(membershipRepository.findByProjectIdOrderByJoinedAtAsc(20L))
                .thenReturn(List.of(ProjectMembership.builder()
                        .project(ownedProject())
                        .user(member)
                        .role(ProjectRole.MEMBER)
                        .build()));

        List<ProjectMemberUserResponse> response = taskService.getAllUsersInProject(20L, OWNER_USERNAME);

        assertEquals("bob", response.getFirst().username());
        assertEquals("Bob Member", response.getFirst().fullName());
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
    void todoQueryUsesMembershipScopedRepositoryMethod() {
        when(taskRepository.findVisibleByStatusAndUsername(Status.TODO, OWNER_USERNAME))
                .thenReturn(List.of());

        assertEquals(List.of(), taskService.getTodoTasks(OWNER_USERNAME));

        verify(taskRepository).findVisibleByStatusAndUsername(Status.TODO, OWNER_USERNAME);
        verify(taskRepository, never()).findByStatus(Status.TODO);
    }

    private Task findTaskFor(Long taskId, String username) {
        return taskRepository.findByIdAndProjectOwnerUsername(taskId, username)
                .or(() -> taskRepository.findByIdAndAssigneeUsername(taskId, username))
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));
    }

    private ProjectMembership membership(String username, ProjectRole role) {
        User user = User.builder()
                .id(OWNER_USERNAME.equals(username) ? 1L : 2L)
                .username(username)
                .active(true)
                .build();
        return ProjectMembership.builder()
                .project(ownedProject())
                .user(user)
                .role(role)
                .build();
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
