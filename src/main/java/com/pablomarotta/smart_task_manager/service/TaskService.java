package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.TaskRequest;
import com.pablomarotta.smart_task_manager.dto.TaskResponse;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.exception.TaskNotFoundException;
import com.pablomarotta.smart_task_manager.exception.UserNotFoundException;
import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.pablomarotta.smart_task_manager.exception.TaskNotFoundException;
import com.pablomarotta.smart_task_manager.exception.UserNotFoundException;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    @Transactional
    public TaskResponse createTask(TaskRequest taskRequest) {
        if (taskRequest == null) {
            throw new IllegalArgumentException("Task request cannot be null");
        }
        
        if (taskRequest.getProjectId() == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        
        try {
            var project = projectRepository.findById(taskRequest.getProjectId())
                    .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + taskRequest.getProjectId()));

            var taskBuilder = Task.builder()
                    .id(null)
                    .title(validateTitle(taskRequest.getTitle()))
                    .description(taskRequest.getDescription())
                    .status(taskRequest.getStatus() != null ? taskRequest.getStatus() : Status.TODO)
                    .project(project)
                    .priority(taskRequest.getPriority())
                    .category(taskRequest.getCategory())
                    .dueDate(validateDueDate(taskRequest.getDueDate()))
                    .position(validatePosition(taskRequest.getPosition()));

            if (taskRequest.getAssigneeId() != null) {
                var assignee = userRepository.findById(taskRequest.getAssigneeId())
                        .orElseThrow(() -> new UserNotFoundException("User not found with id: " + taskRequest.getAssigneeId()));
                taskBuilder.assignee(assignee);
            }

            Task task = taskBuilder.build();
            Task savedTask = taskRepository.save(task);
            return mapToResponse(savedTask);
            
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

        if (task.getAssignee() != null) {
            taskResponse.setAssigneeId(task.getAssignee().getId());
        }

        return taskResponse;
    }

    public List<TaskResponse> getAllTasks() {
        return taskRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<UserResponse> getAllUsersInProject(Long projectId) {
        return userRepository.findAll().stream()
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

    public List<TaskResponse> getTasksByProjectId(Long projectId) {
        return taskRepository.findByProjectId(projectId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<TaskResponse> getTasksByUserId(Long userId) {
        return taskRepository.findByAssigneeId(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<TaskResponse> getInProgressTask(){
        return taskRepository.findByStatus(Status.IN_PROGRESS).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<TaskResponse> getTodoTasks(){
        return taskRepository.findByStatus(Status.TODO).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<TaskResponse> getDoneTasks(){
        return taskRepository.findByStatus(Status.DONE).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<TaskResponse> getBlockedTasks(){
        return taskRepository.findByStatus(Status.BLOCKED).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<TaskResponse> getCancelledTasks(){
        return taskRepository.findByStatus(Status.CANCELLED).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public TaskResponse getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));
        return mapToResponse(task);
    }

    @Transactional
    public TaskResponse updateTask(Long id, TaskRequest taskRequest) {
        if (id == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        if (taskRequest == null) {
            throw new IllegalArgumentException("Task request cannot be null");
        }
        
        try {
            Task task = taskRepository.findById(id)
                    .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));

            if (taskRequest.getTitle() != null) {
                task.setTitle(validateTitle(taskRequest.getTitle()));
            }
            if (taskRequest.getDescription() != null) {
                task.setDescription(taskRequest.getDescription());
            }
            if (taskRequest.getDueDate() != null) {
                task.setDueDate(validateDueDate(taskRequest.getDueDate()));
            }
            if (taskRequest.getStatus() != null) {
                task.setStatus(taskRequest.getStatus());
            }
            if (taskRequest.getPriority() != null) {
                task.setPriority(taskRequest.getPriority());
            }
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
    public void deleteTask(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        
        try {
            Task task = taskRepository.findById(id)
                    .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));
            taskRepository.delete(task);
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete task: " + e.getMessage(), e);
        }
    }

    @Transactional
    public TaskResponse updateTaskStatus(Long id, Status status) {
        if (id == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        
        try {
            Task task = taskRepository.findById(id)
                    .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));

            task.setStatus(status);

            if (status == Status.DONE) {
                task.setCompletedAt(LocalDateTime.now());
            }

            Task updatedTask = taskRepository.save(task);
            return mapToResponse(updatedTask);
            
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update task status: " + e.getMessage(), e);
        }
    }

    @Transactional
    public TaskResponse assignTask(Long taskId, Long userId) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        
        try {
            Task task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));

            if (userId != null) {
                com.pablomarotta.smart_task_manager.model.User user = userRepository.findById(userId)
                        .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
                task.setAssignee(user);
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
    public TaskResponse updateTaskPriority(Long id, Priority priority) {
        if (id == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        if (priority == null) {
            throw new IllegalArgumentException("Priority cannot be null");
        }
        
        try {
            Task task = taskRepository.findById(id)
                    .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));

            task.setPriority(priority);
            Task updatedTask = taskRepository.save(task);
            return mapToResponse(updatedTask);
            
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update task priority: " + e.getMessage(), e);
        }
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
        return position != null ? position : 0;
    }
}
