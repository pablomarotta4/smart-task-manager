package com.pablomarotta.smart_task_manager.controller;

import com.pablomarotta.smart_task_manager.dto.planning.ConfirmProjectGenerationRequest;
import com.pablomarotta.smart_task_manager.dto.planning.GenerateProjectRequest;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationConfirmationResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationDraftResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationRunDetailResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationRunSummaryResponse;
import com.pablomarotta.smart_task_manager.service.ProjectGenerationConfirmationService;
import com.pablomarotta.smart_task_manager.service.ProjectGenerationRunQueryService;
import com.pablomarotta.smart_task_manager.service.ProjectGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/project-generation-runs")
public class ProjectGenerationController {
    private final ProjectGenerationService generationService;
    private final ProjectGenerationConfirmationService confirmationService;
    private final ProjectGenerationRunQueryService queryService;

    public ProjectGenerationController(
            ProjectGenerationService generationService,
            ProjectGenerationConfirmationService confirmationService,
            ProjectGenerationRunQueryService queryService
    ) {
        this.generationService = generationService;
        this.confirmationService = confirmationService;
        this.queryService = queryService;
    }

    @GetMapping
    public List<ProjectGenerationRunSummaryResponse> list(Authentication authentication) {
        return queryService.listRecent(authentication.getName());
    }

    @GetMapping("/{runId}")
    public ProjectGenerationRunDetailResponse get(
            @PathVariable UUID runId,
            Authentication authentication
    ) {
        return queryService.get(runId, authentication.getName());
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
