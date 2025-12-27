package com.pablomarotta.smart_task_manager.controller;

import com.pablomarotta.smart_task_manager.dto.ProjectRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectResponse;
import com.pablomarotta.smart_task_manager.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Slf4j
public class ProjectController {
    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(@Valid @RequestBody ProjectRequest projectRequest) {
        log.info("Creating new project: {}", projectRequest.getName());
        return projectService.createProject(projectRequest);
    }

    @GetMapping
    public java.util.List<ProjectResponse> getAllProjects() {
        log.info("Fetching all projects");
        return projectService.getAllProjects();
    }

    @GetMapping("/{id}")
    public ProjectResponse getProjectById(@PathVariable Long id) {
        log.info("Fetching project with id: {}", id);
        return projectService.getProjectById(id);
    }
}
