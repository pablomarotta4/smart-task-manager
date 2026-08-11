package com.pablomarotta.smart_task_manager.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningContext;
import com.pablomarotta.smart_task_manager.dto.planning.PlanningProjectSnapshot;
import com.pablomarotta.smart_task_manager.dto.planning.PlanningTaskSnapshot;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.exception.TaskNotFoundException;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.model.TaskAcceptanceCriterion;
import com.pablomarotta.smart_task_manager.model.TaskDependency;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskAcceptanceCriterionRepository;
import com.pablomarotta.smart_task_manager.repository.TaskDependencyRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectPlanningContextService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskAcceptanceCriterionRepository criterionRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CapturedContext capture(Long projectId, Long taskId, String username) {
        Project project = projectRepository.findByIdAndOwnerUsername(projectId, username)
                .orElseThrow(() -> new ProjectNotFoundException(
                        "Project not found with id: " + projectId
                ));
        List<Task> tasks = taskRepository.findByProjectIdOrderByPositionAsc(projectId);
        Task targetTask = tasks.stream()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));

        Map<Long, List<String>> criteriaByTask = criterionRepository.findByProjectId(projectId)
                .stream()
                .collect(Collectors.groupingBy(
                        criterion -> criterion.getTask().getId(),
                        Collectors.mapping(TaskAcceptanceCriterion::getCriterion, Collectors.toList())
                ));
        Map<Long, List<Long>> dependenciesByTask = dependencyRepository.findByProjectId(projectId)
                .stream()
                .collect(Collectors.groupingBy(
                        dependency -> dependency.getTask().getId(),
                        Collectors.mapping(
                                dependency -> dependency.getDependsOnTask().getId(),
                                Collectors.toList()
                        )
                ));

        List<PlanningTaskSnapshot> taskSnapshots = tasks.stream()
                .map(task -> snapshot(
                        task,
                        criteriaByTask.getOrDefault(task.getId(), List.of()),
                        dependenciesByTask.getOrDefault(task.getId(), List.of())
                ))
                .toList();
        AIPlanningContext context = new AIPlanningContext(
                ProjectGenerationMode.EXISTING_TASK,
                new PlanningProjectSnapshot(project.getId(), project.getName(), project.getObjective()),
                targetTask.getId(),
                taskSnapshots
        );
        return new CapturedContext(project, targetTask, context, hash(context));
    }

    private PlanningTaskSnapshot snapshot(
            Task task,
            List<String> acceptanceCriteria,
            List<Long> dependencyIds
    ) {
        return new PlanningTaskSnapshot(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getCategory(),
                task.getDueDate(),
                task.getPosition(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getAssignee() == null ? null : task.getAssignee().getUsername(),
                acceptanceCriteria,
                dependencyIds
        );
    }

    private String hash(AIPlanningContext context) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(context);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialized);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not fingerprint project planning context", exception);
        }
    }

    public record CapturedContext(
            Project project,
            Task targetTask,
            AIPlanningContext context,
            String contextHash
    ) {
    }
}
