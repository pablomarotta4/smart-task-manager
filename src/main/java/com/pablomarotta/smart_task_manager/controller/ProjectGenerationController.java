package com.pablomarotta.smart_task_manager.controller;

import com.pablomarotta.smart_task_manager.dto.planning.ConfirmProjectGenerationRequest;
import com.pablomarotta.smart_task_manager.dto.planning.GenerateProjectRequest;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationConfirmationResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationDraftResponse;
import com.pablomarotta.smart_task_manager.service.ProjectGenerationConfirmationService;
import com.pablomarotta.smart_task_manager.service.ProjectGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/project-generation-runs")
public class ProjectGenerationController {
    private final ProjectGenerationService generationService;
    private final ProjectGenerationConfirmationService confirmationService;

    public ProjectGenerationController(
            ProjectGenerationService generationService,
            ProjectGenerationConfirmationService confirmationService
    ) {
        this.generationService = generationService;
        this.confirmationService = confirmationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectGenerationDraftResponse generate(
            @Valid @RequestBody GenerateProjectRequest request,
            Authentication authentication
    ) {
        return generationService.generateDraft(authentication.getName(), request.prompt());
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectGenerationDraftResponse generateForExistingTask(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody GenerateProjectRequest request,
            Authentication authentication
    ) {
        return generationService.generateDraftForTask(
                authentication.getName(),
                projectId,
                taskId,
                request.prompt()
        );
    }

    @PostMapping("/{runId}/confirm")
    public ResponseEntity<ProjectGenerationConfirmationResponse> confirm(
            @PathVariable UUID runId,
            @Valid @RequestBody ConfirmProjectGenerationRequest request,
            Authentication authentication
    ) {
        ProjectGenerationConfirmationResponse response = confirmationService.confirm(
                runId,
                authentication.getName(),
                request.draft()
        );
        HttpStatus status = response.alreadyConfirmed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}
