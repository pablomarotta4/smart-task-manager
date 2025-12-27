package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.TaskRequest;
import com.pablomarotta.smart_task_manager.dto.TaskResponse;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.exception.TaskNotFoundException;
import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        var project = projectRepository.findById(taskRequest.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + taskRequest.getProjectId()));

        var taskBuilder = Task.builder()
                .id(null)
                .title(taskRequest.getTitle())
                .description(taskRequest.getDescription())
                .status(taskRequest.getStatus())
                .project(project)
                .priority(taskRequest.getPriority())
                .category(taskRequest.getCategory())
                .dueDate(taskRequest.getDueDate())
                .position(taskRequest.getPosition());

        if (taskRequest.getAssigneeId() != null) {
            var assignee = userRepository.findById(taskRequest.getAssigneeId())
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + taskRequest.getAssigneeId()));
            taskBuilder.assignee(assignee);
        }

        Task task = taskBuilder.build();
        Task savedTask = taskRepository.save(task);

        return mapToResponse(savedTask);
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
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));

        task.setTitle(taskRequest.getTitle());
        task.setDescription(taskRequest.getDescription());
        task.setDueDate(taskRequest.getDueDate());

        Task updatedTask = taskRepository.save(task);
        return mapToResponse(updatedTask);
    }

    @Transactional
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));
        taskRepository.delete(task);
    }

    @Transactional
    public TaskResponse updateTaskStatus(Long id, Status status) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));

        task.setStatus(status);

        if (status == Status.DONE) {
            task.setCompletedAt(LocalDateTime.now());
        }

        Task updatedTask = taskRepository.save(task);
        return mapToResponse(updatedTask);
    }

    @Transactional
    public TaskResponse assignTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));

        if (userId != null) {
            com.pablomarotta.smart_task_manager.model.User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
            task.setAssignee(user);
        } else {
            task.setAssignee(null);
        }

        Task updatedTask = taskRepository.save(task);
        return mapToResponse(updatedTask);
    }

    @Transactional
    public TaskResponse updateTaskPriority(Long id, Priority priority) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));

        task.setPriority(priority);

        Task updatedTask = taskRepository.save(task);
        return mapToResponse(updatedTask);
    }
}
