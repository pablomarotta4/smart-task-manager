package com.pablomarotta.smart_task_manager.controller;

import com.pablomarotta.smart_task_manager.dto.TaskRequest;
import com.pablomarotta.smart_task_manager.dto.TaskResponse;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Slf4j
public class TaskController {
    private final TaskService taskService;

    @PostMapping("/newtask")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@RequestBody TaskRequest taskRequest) {
        log.info("Creating new task: {}", taskRequest.getTitle());
        return taskService.createTask(taskRequest);
    }

    @GetMapping("/alltasks")
    public List<TaskResponse> getAllTasks() {
        log.info("Fetching all tasks");
        return taskService.getAllTasks();
    }

    @GetMapping("/project/{projectId}")
    public List<TaskResponse> getTasksByProject(@PathVariable Long projectId) {
        log.info("Fetching tasks for project: {}", projectId);
        return taskService.getTasksByProjectId(projectId);
    }

    @GetMapping("/user/{userId}")
    public List<TaskResponse> getTasksByUser(@PathVariable Long userId) {
        log.info("Fetching tasks for user: {}", userId);
        return taskService.getTasksByUserId(userId);
    }

    @GetMapping("/status/todo")
    public List<TaskResponse> getTodoTasks() {
        log.info("Fetching TODO tasks");
        return taskService.getTodoTasks();
    }

    @GetMapping("/status/in-progress")
    public List<TaskResponse> getInProgressTasks() {
        log.info("Fetching IN_PROGRESS tasks");
        return taskService.getInProgressTask();
    }

    @GetMapping("/status/done")
    public List<TaskResponse> getDoneTasks() {
        log.info("Fetching DONE tasks");
        return taskService.getDoneTasks();
    }

    @GetMapping("/status/blocked")
    public List<TaskResponse> getBlockedTasks() {
        log.info("Fetching BLOCKED tasks");
        return taskService.getBlockedTasks();
    }

    @GetMapping("/status/cancelled")
    public List<TaskResponse> getCancelledTasks() {
        log.info("Fetching CANCELLED tasks");
        return taskService.getCancelledTasks();
    }

    @GetMapping("/project/{projectId}/users")
    public List<UserResponse> getAllUsersInProject(@PathVariable Long projectId) {
        log.info("Fetching all users in project: {}", projectId);
        return taskService.getAllUsersInProject(projectId);
    }

    @GetMapping("/{id}")
    public TaskResponse getTaskById(@PathVariable Long id) {
        log.info("Fetching task with id: {}", id);
        return taskService.getTaskById(id);
    }

    @PutMapping("/{id}")
    public TaskResponse updateTask(@PathVariable Long id, @RequestBody TaskRequest taskRequest) {
        log.info("Updating task with id: {}", id);
        return taskService.updateTask(id, taskRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long id) {
        log.info("Deleting task with id: {}", id);
        taskService.deleteTask(id);
    }

    @PatchMapping("/{id}/status")
    public TaskResponse updateTaskStatus(@PathVariable Long id, @RequestParam Status status) {
        log.info("Updating status of task {} to {}", id, status);
        return taskService.updateTaskStatus(id, status);
    }

    @PatchMapping("/{id}/assign")
    public TaskResponse assignTask(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        log.info("Assigning task {} to user {}", id, userId);
        return taskService.assignTask(id, userId);
    }

    @PatchMapping("/{id}/priority")
    public TaskResponse updateTaskPriority(@PathVariable Long id, @RequestParam Priority priority) {
        log.info("Updating priority of task {} to {}", id, priority);
        return taskService.updateTaskPriority(id, priority);
    }

}
