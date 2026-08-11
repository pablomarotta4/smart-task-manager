package com.pablomarotta.smart_task_manager.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.planning.PlanQualityReport;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationRunDetailResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationRunSummaryResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectPlanDraft;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationRun;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;
import com.pablomarotta.smart_task_manager.repository.ProjectGenerationRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectGenerationRunQueryService {
    private static final long STALE_PROCESSING_MINUTES = 2;

    private final ProjectGenerationRunRepository runRepository;
    private final ObjectMapper objectMapper;

    public ProjectGenerationRunQueryService(
            ProjectGenerationRunRepository runRepository,
            ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProjectGenerationRunSummaryResponse> listRecent(String username) {
        return runRepository.findTop10ByRequestedByUsernameOrderByUpdatedAtDesc(username)
                .stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectGenerationRunDetailResponse get(UUID runId, String username) {
        ProjectGenerationRun run = runRepository.findByIdAndRequestedByUsername(runId, username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Generation run not found"
                ));
        ProjectGenerationRunSummaryResponse summary = summary(run);
        return new ProjectGenerationRunDetailResponse(
                summary.runId(),
                summary.mode(),
                summary.status(),
                summary.prompt(),
                summary.attemptCount(),
                summary.projectId(),
                summary.projectName(),
                summary.targetTaskId(),
                summary.targetTaskTitle(),
                summary.errorCode(),
                summary.retryable(),
                summary.createdAt(),
                summary.updatedAt(),
                read(run.getDraftJson(), ProjectPlanDraft.class),
                read(run.getQualityJson(), PlanQualityReport.class),
                run.getRevisionCount(),
                run.getModelName()
        );
    }

    static boolean isRetryable(ProjectGenerationRun run, LocalDateTime now) {
        if (run.getStatus() == ProjectGenerationStatus.FAILED) {
            return true;
        }
        return run.getStatus() == ProjectGenerationStatus.PROCESSING
                && run.getUpdatedAt() != null
                && !run.getUpdatedAt().isAfter(now.minusMinutes(STALE_PROCESSING_MINUTES));
    }

    private ProjectGenerationRunSummaryResponse summary(ProjectGenerationRun run) {
        return new ProjectGenerationRunSummaryResponse(
                run.getId(),
                run.getMode(),
                run.getStatus(),
                run.getPrompt(),
                run.getAttemptCount(),
                run.getProject() == null ? null : run.getProject().getId(),
                run.getProject() == null ? null : run.getProject().getName(),
                run.getTargetTask() == null ? null : run.getTargetTask().getId(),
                run.getTargetTask() == null ? null : run.getTargetTask().getTitle(),
                run.getErrorCode(),
                isRetryable(run, LocalDateTime.now()),
                run.getCreatedAt(),
                run.getUpdatedAt()
        );
    }

    private <T> T read(String json, Class<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not restore generation run data", exception);
        }
    }
}
