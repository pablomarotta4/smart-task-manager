package com.pablomarotta.smart_task_manager.controller;

import com.pablomarotta.smart_task_manager.dto.ProjectRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectResponse;
import com.pablomarotta.smart_task_manager.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
public class ProjectController {
    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(
            @Valid @RequestBody ProjectRequest projectRequest,
            Principal principal
    ) {
        log.info("Creating new project: {}", projectRequest.getName());
        return projectService.createProject(projectRequest, principal.getName());
    }

    @GetMapping
    public java.util.List<ProjectResponse> getAllProjects(Principal principal) {
        log.info("Fetching all projects");
        return projectService.getAllProjects(principal.getName());
    }

    @GetMapping("/{id}")
    public ProjectResponse getProjectById(@PathVariable Long id, Principal principal) {
        log.info("Fetching project with id: {}", id);
        return projectService.getProjectById(id, principal.getName());
    }

    @PutMapping("/{id}")
    public ProjectResponse updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest projectRequest,
            Principal principal
    ) {
        log.info("Updating project with id: {}", id);
        return projectService.updateProject(id, projectRequest, principal.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable Long id, Principal principal) {
        log.info("Deleting project with id: {}", id);
        projectService.deleteProject(id, principal.getName());
    }
}
