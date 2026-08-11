package com.pablomarotta.smart_task_manager.controller;

import com.pablomarotta.smart_task_manager.dto.TaskRequest;
import com.pablomarotta.smart_task_manager.dto.TaskResponse;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Slf4j
public class TaskController {
    private final TaskService taskService;

    @PostMapping("/newtask")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@Valid @RequestBody TaskRequest taskRequest, Principal principal) {
        log.info("Creating new task: {}", taskRequest.getTitle());
        return taskService.createTask(taskRequest, principal.getName());
    }

    @GetMapping("/alltasks")
    public List<TaskResponse> getAllTasks(Principal principal) {
        log.info("Fetching all tasks");
        return taskService.getAllTasks(principal.getName());
    }

    @GetMapping("/my-work")
    public List<TaskResponse> getMyWork(Principal principal) {
        log.info("Fetching assigned work for current user");
        return taskService.getMyWork(principal.getName());
    }

    @GetMapping("/project/{projectId}")
    public List<TaskResponse> getTasksByProject(@PathVariable Long projectId, Principal principal) {
        log.info("Fetching tasks for project: {}", projectId);
        return taskService.getTasksByProjectId(projectId, principal.getName());
    }

    @GetMapping("/user/{userId}")
    public List<TaskResponse> getTasksByUser(@PathVariable Long userId, Principal principal) {
        log.info("Fetching tasks for user: {}", userId);
        return taskService.getTasksByUserId(userId, principal.getName());
    }

    @GetMapping("/status/todo")
    public List<TaskResponse> getTodoTasks(Principal principal) {
        log.info("Fetching TODO tasks");
        return taskService.getTodoTasks(principal.getName());
    }

    @GetMapping("/status/in-progress")
    public List<TaskResponse> getInProgressTasks(Principal principal) {
        log.info("Fetching IN_PROGRESS tasks");
        return taskService.getInProgressTask(principal.getName());
    }

    @GetMapping("/status/done")
    public List<TaskResponse> getDoneTasks(Principal principal) {
        log.info("Fetching DONE tasks");
        return taskService.getDoneTasks(principal.getName());
    }

    @GetMapping("/status/blocked")
    public List<TaskResponse> getBlockedTasks(Principal principal) {
        log.info("Fetching BLOCKED tasks");
        return taskService.getBlockedTasks(principal.getName());
    }

    @GetMapping("/status/cancelled")
    public List<TaskResponse> getCancelledTasks(Principal principal) {
        log.info("Fetching CANCELLED tasks");
        return taskService.getCancelledTasks(principal.getName());
    }

    @GetMapping("/project/{projectId}/users")
    public List<UserResponse> getAllUsersInProject(@PathVariable Long projectId, Principal principal) {
        log.info("Fetching all users in project: {}", projectId);
        return taskService.getAllUsersInProject(projectId, principal.getName());
    }

    @GetMapping("/{id}")
    public TaskResponse getTaskById(@PathVariable Long id, Principal principal) {
        log.info("Fetching task with id: {}", id);
        return taskService.getTaskById(id, principal.getName());
    }

    @PutMapping("/{id}")
    public TaskResponse updateTask(@PathVariable Long id, @Valid @RequestBody TaskRequest taskRequest, Principal principal) {
        log.info("Updating task with id: {}", id);
        return taskService.updateTask(id, taskRequest, principal.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long id, Principal principal) {
        log.info("Deleting task with id: {}", id);
        taskService.deleteTask(id, principal.getName());
    }

    @PatchMapping("/{id}/status")
    public TaskResponse updateTaskStatus(@PathVariable Long id, @RequestParam Status status, Principal principal) {
        log.info("Updating status of task {} to {}", id, status);
        return taskService.updateTaskStatus(id, status, principal.getName());
    }

    @PatchMapping("/{id}/assign")
    public TaskResponse assignTask(@PathVariable Long id, @RequestParam(required = false) Long userId, Principal principal) {
        log.info("Assigning task {} to user {}", id, userId);
        return taskService.assignTask(id, userId, principal.getName());
    }

    @PatchMapping("/{id}/priority")
    public TaskResponse updateTaskPriority(@PathVariable Long id, @RequestParam Priority priority, Principal principal) {
        log.info("Updating priority of task {} to {}", id, priority);
        return taskService.updateTaskPriority(id, priority, principal.getName());
    }

}
