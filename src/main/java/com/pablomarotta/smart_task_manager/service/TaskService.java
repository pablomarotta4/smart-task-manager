package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.*;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.exception.TaskNotFoundException;
import com.pablomarotta.smart_task_manager.exception.UserNotFoundException;
import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectMembershipRepository;
import com.pablomarotta.smart_task_manager.repository.TaskAcceptanceCriterionRepository;
import com.pablomarotta.smart_task_manager.repository.TaskDependencyRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final TaskAcceptanceCriterionRepository acceptanceCriterionRepository;
    private final TaskDependencyRepository dependencyRepository;

    @Transactional
    public TaskResponse createTask(TaskRequest taskRequest, String username) {
        if (taskRequest == null) {
            throw new IllegalArgumentException("Task request cannot be null");
        }
        
        if (taskRequest.getProjectId() == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        
        try {
            var project = projectRepository.findByIdAndOwnerUsername(taskRequest.getProjectId(), username)
                    .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + taskRequest.getProjectId()));

            var taskBuilder = Task.builder()
                    .id(null)
                    .title(validateTitle(taskRequest.getTitle()))
                    .description(normalizeOptionalText(taskRequest.getDescription()))
                    .status(taskRequest.getStatus() != null ? taskRequest.getStatus() : Status.TODO)
                    .project(project)
                    .createdBy(project.getOwner())
                    .priority(taskRequest.getPriority())
                    .category(normalizeOptionalText(taskRequest.getCategory()))
                    .dueDate(validateDueDate(taskRequest.getDueDate()))
                    .position(taskRequest.getPosition() == null
                            ? Math.toIntExact(taskRepository.countByProjectId(project.getId()))
                            : validatePosition(taskRequest.getPosition()));

            if (taskRequest.getAssigneeId() != null) {
                taskBuilder.assignee(getActiveProjectMember(project.getId(), taskRequest.getAssigneeId()));
            }

            Task task = taskBuilder.build();
            Task savedTask = taskRepository.save(task);
            return mapToResponse(savedTask);
            
        } catch (ProjectNotFoundException | UserNotFoundException | IllegalArgumentException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Data integrity violation: " + e.getMessage(), e);
        } catch (TransactionSystemException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Transaction failed: " + e.getMostSpecificCause().getMessage(), e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create task: " + e.getMessage(), e);
        }
    }

    private TaskResponse mapToResponse(Task task){
        TaskResponse taskResponse = new TaskResponse();
        taskResponse.setId(task.getId());
        taskResponse.setTitle(task.getTitle());
        taskResponse.setDescription(task.getDescription());
        taskResponse.setStatus(task.getStatus());
        taskResponse.setPriority(task.getPriority());
        taskResponse.setCategory(task.getCategory());
        taskResponse.setDueDate(task.getDueDate());
        taskResponse.setPosition(task.getPosition());
        taskResponse.setProjectId(task.getProject().getId());
        taskResponse.setProjectName(task.getProject().getName());
        if (task.getParentTask() != null) {
            taskResponse.setParentTaskId(task.getParentTask().getId());
        }

        if (task.getAssignee() != null) {
            taskResponse.setAssigneeId(task.getAssignee().getId());
            taskResponse.setAssigneeUsername(task.getAssignee().getUsername());
        }
        if (task.getCreatedBy() != null) {
            taskResponse.setCreatedById(task.getCreatedBy().getId());
            taskResponse.setCreatedByUsername(task.getCreatedBy().getUsername());
        }
        taskResponse.setCreatedAt(task.getCreatedAt() == null ? null : task.getCreatedAt().toString());
        taskResponse.setUpdatedAt(task.getUpdatedAt() == null ? null : task.getUpdatedAt().toString());
        taskResponse.setCompletedAt(task.getCompletedAt() == null ? null : task.getCompletedAt().toString());
        taskResponse.setAiCategory(task.getAiCategory());
        taskResponse.setAiSuggestedDueDays(task.getAiSuggestedDueDays());
        taskResponse.setAiSuggestedDueDate(task.getAiSuggestedDueDate());
        taskResponse.setAiSummary(task.getAiSummary());

        return taskResponse;
    }

    public List<TaskResponse> getAllTasks(String username) {
        return taskRepository.findByProjectOwnerUsername(username).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getMyWork(String username) {
        List<Task> tasks = taskRepository.findByAssigneeUsernameOrderByDueDateAscPositionAsc(username);
        Map<Long, List<String>> acceptanceCriteria = acceptanceCriterionRepository
                .findByTaskAssigneeUsername(username)
                .stream()
                .collect(Collectors.groupingBy(
                        criterion -> criterion.getTask().getId(),
                        Collectors.mapping(
                                com.pablomarotta.smart_task_manager.model.TaskAcceptanceCriterion::getCriterion,
                                Collectors.toList()
                        )
                ));
        Map<Long, List<String>> dependencies = dependencyRepository
                .findByTaskAssigneeUsername(username)
                .stream()
                .collect(Collectors.groupingBy(
                        dependency -> dependency.getTask().getId(),
                        Collectors.mapping(
                                dependency -> dependency.getDependsOnTask().getPlanningClientId(),
                                Collectors.toList()
                        )
                ));

        return tasks.stream()
                .map(task -> mapToProjectResponse(
                        task,
                        acceptanceCriteria.getOrDefault(task.getId(), List.of()),
                        dependencies.getOrDefault(task.getId(), List.of())
                ))
                .toList();
    }

    public List<UserResponse> getAllUsersInProject(Long projectId, String username) {
        var project = getOwnedProject(projectId, username);
        Map<Long, com.pablomarotta.smart_task_manager.model.User> projectUsers = new LinkedHashMap<>();
        projectUsers.put(project.getOwner().getId(), project.getOwner());
        taskRepository.findByProjectIdOrderByPositionAsc(projectId).stream()
                .map(Task::getAssignee)
                .filter(assignee -> assignee != null)
                .forEach(assignee -> projectUsers.putIfAbsent(assignee.getId(), assignee));

        return projectUsers.values().stream()
                .map(this::mapUserToResponse)
                .toList();
    }

    private UserResponse mapUserToResponse(com.pablomarotta.smart_task_manager.model.User user) {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setUsername(user.getUsername());
        userResponse.setEmail(user.getEmail());
        return userResponse;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByProjectId(Long projectId, String username) {
        getOwnedProject(projectId, username);

        Map<Long, List<String>> acceptanceCriteria = acceptanceCriterionRepository
                .findByProjectId(projectId)
                .stream()
                .collect(Collectors.groupingBy(
                        criterion -> criterion.getTask().getId(),
                        Collectors.mapping(
                                com.pablomarotta.smart_task_manager.model.TaskAcceptanceCriterion::getCriterion,
                                Collectors.toList()
                        )
                ));
        Map<Long, List<String>> dependencies = dependencyRepository
                .findByProjectId(projectId)
                .stream()
                .collect(Collectors.groupingBy(
                        dependency -> dependency.getTask().getId(),
                        Collectors.mapping(
                                dependency -> dependency.getDependsOnTask().getPlanningClientId(),
                                Collectors.toList()
                        )
                ));

        return taskRepository.findByProjectIdOrderByPositionAsc(projectId).stream()
                .map(task -> mapToProjectResponse(
                        task,
                        acceptanceCriteria.getOrDefault(task.getId(), List.of()),
                        dependencies.getOrDefault(task.getId(), List.of())
                ))
                .toList();
    }

    private TaskResponse mapToProjectResponse(
            Task task,
            List<String> acceptanceCriteria,
            List<String> dependencies
    ) {
        TaskResponse response = mapToResponse(task);
        response.setEstimatedHours(task.getEstimatedHours());
        response.setPlanningClientId(task.getPlanningClientId());
        response.setAiSummary(task.getAiSummary());
        response.setAcceptanceCriteria(acceptanceCriteria);
        response.setDependsOn(dependencies);
        return response;
    }

    public List<TaskResponse> getTasksByUserId(Long userId, String username) {
        return taskRepository.findByAssigneeIdAndProjectOwnerUsername(userId, username).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<TaskResponse> getInProgressTask(String username){
        return getTasksByStatus(Status.IN_PROGRESS, username);
    }

    public List<TaskResponse> getTodoTasks(String username){
        return getTasksByStatus(Status.TODO, username);
    }

    public List<TaskResponse> getDoneTasks(String username){
        return getTasksByStatus(Status.DONE, username);
    }

    public List<TaskResponse> getBlockedTasks(String username){
        return getTasksByStatus(Status.BLOCKED, username);
    }

    public List<TaskResponse> getCancelledTasks(String username){
        return getTasksByStatus(Status.CANCELLED, username);
    }

    private List<TaskResponse> getTasksByStatus(Status status, String username) {
        return taskRepository.findByStatusAndProjectOwnerUsername(status, username).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public TaskResponse getTaskById(Long id, String username) {
        Task task = getEditableTask(id, username);
        return mapToResponse(task);
    }

    @Transactional
    public TaskResponse updateTask(Long id, TaskRequest taskRequest, String username) {
        if (id == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        if (taskRequest == null) {
            throw new IllegalArgumentException("Task request cannot be null");
        }
        
        try {
            Task task = getEditableTask(id, username);
            if (!task.getProject().getId().equals(taskRequest.getProjectId())) {
                throw new IllegalArgumentException("Task cannot be moved to another project");
            }
            assertContributorKeepsOwnerControlledFields(task, taskRequest, username);

            task.setTitle(validateTitle(taskRequest.getTitle()));
            task.setDescription(normalizeOptionalText(taskRequest.getDescription()));
            task.setDueDate(validateDueDate(taskRequest.getDueDate()));
            applyStatus(task, taskRequest.getStatus());
            task.setPriority(taskRequest.getPriority());
            task.setCategory(normalizeOptionalText(taskRequest.getCategory()));
            applyAssignment(task, taskRequest.getAssigneeId(), username);
            if (taskRequest.getPosition() != null) {
                task.setPosition(validatePosition(taskRequest.getPosition()));
            }

            Task updatedTask = taskRepository.save(task);
            return mapToResponse(updatedTask);
            
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update task: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteTask(Long id, String username) {
        if (id == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        
        try {
            Task task = getOwnedTask(id, username);
            taskRepository.delete(task);
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete task: " + e.getMessage(), e);
        }
    }

    @Transactional
    public TaskResponse updateTaskStatus(Long id, Status status, String username) {
        if (id == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        
        try {
            Task task = getEditableTask(id, username);

            applyStatus(task, status);

            Task updatedTask = taskRepository.save(task);
            return mapToResponse(updatedTask);
            
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update task status: " + e.getMessage(), e);
        }
    }

    @Transactional
    public TaskResponse assignTask(Long taskId, Long userId, String username) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        
        try {
            Task task = getOwnedTask(taskId, username);

            if (userId != null) {
                task.setAssignee(getActiveProjectMember(task.getProject().getId(), userId));
            } else {
                task.setAssignee(null);
            }

            Task updatedTask = taskRepository.save(task);
            return mapToResponse(updatedTask);
            
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to assign task: " + e.getMessage(), e);
        }
    }

    @Transactional
    public TaskResponse updateTaskPriority(Long id, Priority priority, String username) {
        if (id == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        if (priority == null) {
            throw new IllegalArgumentException("Priority cannot be null");
        }
        
        try {
            Task task = getOwnedTask(id, username);

            task.setPriority(priority);
            Task updatedTask = taskRepository.save(task);
            return mapToResponse(updatedTask);
            
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update task priority: " + e.getMessage(), e);
        }
    }

    private com.pablomarotta.smart_task_manager.model.Project getOwnedProject(Long projectId, String username) {
        return projectRepository.findByIdAndOwnerUsername(projectId, username)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));
    }

    private Task getOwnedTask(Long taskId, String username) {
        return taskRepository.findByIdAndProjectOwnerUsername(taskId, username)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));
    }

    private Task getEditableTask(Long taskId, String username) {
        return taskRepository.findByIdAndProjectOwnerUsername(taskId, username)
                .or(() -> taskRepository.findByIdAndAssigneeUsername(taskId, username))
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));
    }

    private void applyAssignment(Task task, Long assigneeId, String username) {
        boolean owner = task.getProject().getOwner().getUsername().equals(username);
        Long currentAssigneeId = task.getAssignee() == null ? null : task.getAssignee().getId();
        if (!owner) {
            if (!Objects.equals(currentAssigneeId, assigneeId)) {
                throw new AccessDeniedException("Only the project owner can change task assignment");
            }
            return;
        }
        task.setAssignee(assigneeId == null
                ? null
                : getActiveProjectMember(task.getProject().getId(), assigneeId));
    }

    private void assertContributorKeepsOwnerControlledFields(
            Task task,
            TaskRequest request,
            String username
    ) {
        if (task.getProject().getOwner().getUsername().equals(username)) {
            return;
        }
        Long currentAssigneeId = task.getAssignee() == null ? null : task.getAssignee().getId();
        if (!Objects.equals(currentAssigneeId, request.getAssigneeId())) {
            throw new AccessDeniedException("Only the project owner can change task assignment");
        }
        if (!Objects.equals(task.getPriority(), request.getPriority())) {
            throw new AccessDeniedException("Only the project owner can change task priority");
        }
    }

    private com.pablomarotta.smart_task_manager.model.User getActiveProjectMember(
            Long projectId,
            Long userId
    ) {
        com.pablomarotta.smart_task_manager.model.User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("Inactive users cannot be assigned tasks");
        }
        if (!membershipRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new IllegalArgumentException("User is not a member of this project");
        }
        return user;
    }

    private void applyStatus(Task task, Status status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        task.setStatus(status);
        if (status == Status.DONE) {
            if (task.getCompletedAt() == null) {
                task.setCompletedAt(LocalDateTime.now());
            }
        } else {
            task.setCompletedAt(null);
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
    
    // Validation helper methods
    private String validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Task title cannot be empty");
        }
        if (title.length() > 255) {
            throw new IllegalArgumentException("Task title cannot exceed 255 characters");
        }
        return title.trim();
    }
    
    private LocalDate validateDueDate(LocalDate dueDate) {
        if (dueDate != null && dueDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Due date cannot be in the past");
        }
        return dueDate;
    }
    
    private Integer validatePosition(Integer position) {
        if (position != null && position < 0) {
            throw new IllegalArgumentException("Position cannot be negative");
        }
        return position;
    }
}
