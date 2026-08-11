package com.pablomarotta.smart_task_manager.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.client.AIPlanningClient;
import com.pablomarotta.smart_task_manager.client.AIPlanningUnavailableException;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningResponse;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningContext;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationDraftResponse;
import com.pablomarotta.smart_task_manager.exception.UserNotFoundException;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationRun;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectGenerationRunRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProjectGenerationService {
    private final ProjectGenerationRunRepository runRepository;
    private final UserRepository userRepository;
    private final AIPlanningClient aiPlanningClient;
    private final ProjectPlanningContextService contextService;
    private final ProjectGenerationRunRetryClaimService retryClaimService;
    private final ObjectMapper objectMapper;

    public ProjectGenerationService(
            ProjectGenerationRunRepository runRepository,
            UserRepository userRepository,
            AIPlanningClient aiPlanningClient,
            ProjectPlanningContextService contextService,
            ProjectGenerationRunRetryClaimService retryClaimService,
            ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.userRepository = userRepository;
        this.aiPlanningClient = aiPlanningClient;
        this.contextService = contextService;
        this.retryClaimService = retryClaimService;
        this.objectMapper = objectMapper;
    }

    public ProjectGenerationDraftResponse generateDraft(String username, String prompt) {
        User requester = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
        ProjectGenerationRun run = ProjectGenerationRun.builder()
                .id(UUID.randomUUID())
                .requestedBy(requester)
                .prompt(prompt.trim())
                .mode(ProjectGenerationMode.NEW_PROJECT)
                .status(ProjectGenerationStatus.PROCESSING)
                .build();
        runRepository.save(run);

        return generate(run, null);
    }

    public ProjectGenerationDraftResponse generateDraftForTask(
            String username,
            Long projectId,
            Long taskId,
            String prompt
    ) {
        User requester = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with username: " + username
                ));
        ProjectPlanningContextService.CapturedContext captured = contextService.capture(
                projectId,
                taskId,
                username
        );
        ProjectGenerationRun run = ProjectGenerationRun.builder()
                .id(UUID.randomUUID())
                .requestedBy(requester)
                .prompt(prompt.trim())
                .mode(ProjectGenerationMode.EXISTING_TASK)
                .status(ProjectGenerationStatus.PROCESSING)
                .project(captured.project())
                .targetTask(captured.targetTask())
                .contextHash(captured.contextHash())
                .build();
        runRepository.save(run);

        return generate(run, captured.context());
    }

    public ProjectGenerationDraftResponse retryDraft(UUID runId, String username) {
        ProjectGenerationRunRetryClaimService.RetryClaim claim = retryClaimService.claim(
                runId,
                username
        );
        return generate(claim.run(), claim.context());
    }

    private ProjectGenerationDraftResponse generate(
            ProjectGenerationRun run,
            AIPlanningContext context
    ) {
        try {
            AIPlanningResponse response = context == null
                    ? aiPlanningClient.generatePlan(run.getId(), run.getPrompt())
                    : aiPlanningClient.generatePlan(run.getId(), run.getPrompt(), context);
            run.setDraftJson(writeJson(response.draft()));
            run.setQualityJson(writeJson(response.quality()));
            run.setRevisionCount(response.revisionCount());
            run.setModelName(response.model());
            run.setStatus(ProjectGenerationStatus.DRAFT_READY);
            runRepository.save(run);
            return new ProjectGenerationDraftResponse(
                    run.getId(),
                    run.getStatus(),
                    response.draft(),
                    response.quality(),
                    response.revisionCount(),
                    response.model()
            );
        } catch (AIPlanningUnavailableException exception) {
            run.setStatus(ProjectGenerationStatus.FAILED);
            run.setErrorCode("AI_PLANNING_UNAVAILABLE");
            runRepository.save(run);
            throw exception;
        } catch (RuntimeException exception) {
            run.setStatus(ProjectGenerationStatus.FAILED);
            run.setErrorCode("AI_PLANNING_FAILED");
            runRepository.save(run);
            throw new AIPlanningUnavailableException("AI planning failed", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize project generation data", exception);
        }
    }
}
