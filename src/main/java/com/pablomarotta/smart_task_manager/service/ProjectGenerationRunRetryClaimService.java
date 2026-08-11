package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningContext;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationRun;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;
import com.pablomarotta.smart_task_manager.repository.ProjectGenerationRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ProjectGenerationRunRetryClaimService {
    private final ProjectGenerationRunRepository runRepository;
    private final ProjectPlanningContextService contextService;

    public ProjectGenerationRunRetryClaimService(
            ProjectGenerationRunRepository runRepository,
            ProjectPlanningContextService contextService
    ) {
        this.runRepository = runRepository;
        this.contextService = contextService;
    }

    @Transactional
    public RetryClaim claim(UUID runId, String username) {
        ProjectGenerationRun run = runRepository.findLockedById(runId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Generation run not found"
                ));
        if (!run.getRequestedBy().getUsername().equals(username)) {
            throw new AccessDeniedException("Only the generation run owner can retry it");
        }
        if (!ProjectGenerationRunQueryService.isRetryable(run, LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Generation run is not ready for retry"
            );
        }

        AIPlanningContext context = null;
        if (run.getMode() == ProjectGenerationMode.EXISTING_TASK) {
            if (run.getProject() == null || run.getTargetTask() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The selected planning target is no longer available"
                );
            }
            ProjectPlanningContextService.CapturedContext captured = contextService.capture(
                    run.getProject().getId(),
                    run.getTargetTask().getId(),
                    username
            );
            run.setProject(captured.project());
            run.setTargetTask(captured.targetTask());
            run.setContextHash(captured.contextHash());
            context = captured.context();
        }

        run.setStatus(ProjectGenerationStatus.PROCESSING);
        run.setAttemptCount(run.getAttemptCount() + 1);
        run.setErrorCode(null);
        run.setDraftJson(null);
        run.setQualityJson(null);
        run.setRevisionCount(null);
        run.setModelName(null);
        runRepository.save(run);
        return new RetryClaim(run, context);
    }

    public record RetryClaim(
            ProjectGenerationRun run,
            AIPlanningContext context
    ) {
    }
}
