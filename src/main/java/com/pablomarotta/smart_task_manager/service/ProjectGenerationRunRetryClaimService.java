package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningContext;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationRun;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;
import com.pablomarotta.smart_task_manager.repository.ProjectGenerationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectGenerationRunRetryClaimService {
    private final ProjectGenerationRunRepository runRepository;
    private final ProjectPlanningContextService contextService;
    private final ProjectAccessPolicy accessPolicy;

    @Transactional
    public RetryClaim claim(UUID runId, String username) {
        ProjectGenerationRun run = runRepository.findLockedByIdAndRequestedByUsername(runId, username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Generation run not found"
                ));
        requireCurrentManagerForExistingProjectRun(run, username);
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

    private void requireCurrentManagerForExistingProjectRun(ProjectGenerationRun run, String username) {
        if (run.getMode() != ProjectGenerationMode.EXISTING_TASK) {
            return;
        }
        if (run.getProject() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected planning target is no longer available"
            );
        }
        accessPolicy.requireManager(run.getProject().getId(), username);
    }

    public record RetryClaim(
            ProjectGenerationRun run,
            AIPlanningContext context
    ) {
    }
}
