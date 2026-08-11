package com.pablomarotta.smart_task_manager.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.planning.PlanningTicketDraft;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationConfirmationResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectPlanDraft;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationRun;
import com.pablomarotta.smart_task_manager.model.ProjectMembership;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.model.TaskAcceptanceCriterion;
import com.pablomarotta.smart_task_manager.model.TaskDependency;
import com.pablomarotta.smart_task_manager.repository.ProjectGenerationRunRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectMembershipRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskAcceptanceCriterionRepository;
import com.pablomarotta.smart_task_manager.repository.TaskDependencyRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectGenerationConfirmationService {
    private final ProjectGenerationRunRepository runRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskAcceptanceCriterionRepository criterionRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final ObjectMapper objectMapper;

    public ProjectGenerationConfirmationService(
            ProjectGenerationRunRepository runRepository,
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            TaskAcceptanceCriterionRepository criterionRepository,
            TaskDependencyRepository dependencyRepository,
            ProjectMembershipRepository membershipRepository,
            ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.criterionRepository = criterionRepository;
        this.dependencyRepository = dependencyRepository;
        this.membershipRepository = membershipRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProjectGenerationConfirmationResponse confirm(
            UUID runId,
            String username,
            ProjectPlanDraft draft
    ) {
        ProjectGenerationRun run = runRepository.findLockedById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Generation run not found"));
        if (!run.getRequestedBy().getUsername().equals(username)) {
            throw new AccessDeniedException("Only the generation run owner can confirm it");
        }
        if (run.getStatus() == ProjectGenerationStatus.CONFIRMED) {
            List<Long> taskIds = taskRepository.findByGenerationRunIdOrderByPositionAsc(runId)
                    .stream()
                    .map(Task::getId)
                    .toList();
            return response(run, taskIds, true);
        }
        if (run.getStatus() != ProjectGenerationStatus.DRAFT_READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Generation run is not ready");
        }
        validateTicketGraph(draft);

        Project project = projectRepository.save(Project.builder()
                .name(draft.name().trim())
                .objective(draft.objective().trim())
                .owner(run.getRequestedBy())
                .build());
        membershipRepository.save(ProjectMembership.builder()
                .project(project)
                .user(run.getRequestedBy())
                .build());

        List<Task> tasks = new ArrayList<>();
        for (int index = 0; index < draft.tickets().size(); index++) {
            PlanningTicketDraft ticket = draft.tickets().get(index);
            tasks.add(Task.builder()
                    .project(project)
                    .title(ticket.title().trim())
                    .description(ticket.description().trim())
                    .status(Status.TODO)
                    .priority(ticket.priority())
                    .position(index)
                    .createdBy(run.getRequestedBy())
                    .dueDate(ticket.dueInDays() == null ? null : LocalDate.now().plusDays(ticket.dueInDays()))
                    .category(ticket.category())
                    .planningClientId(ticket.clientId())
                    .estimatedHours(BigDecimal.valueOf(ticket.estimatedHours()))
                    .generationRun(run)
                    .build());
        }
        List<Task> savedTasks = taskRepository.saveAll(tasks);
        Map<String, Task> tasksByClientId = new HashMap<>();
        for (Task task : savedTasks) {
            tasksByClientId.put(task.getPlanningClientId(), task);
        }

        List<TaskAcceptanceCriterion> criteria = new ArrayList<>();
        List<TaskDependency> dependencies = new ArrayList<>();
        for (PlanningTicketDraft ticket : draft.tickets()) {
            Task task = tasksByClientId.get(ticket.clientId());
            for (int index = 0; index < ticket.acceptanceCriteria().size(); index++) {
                criteria.add(TaskAcceptanceCriterion.builder()
                        .task(task)
                        .criterion(ticket.acceptanceCriteria().get(index).trim())
                        .position(index)
                        .build());
            }
            for (String dependencyId : ticket.dependsOn()) {
                dependencies.add(TaskDependency.builder()
                        .task(task)
                        .dependsOnTask(tasksByClientId.get(dependencyId))
                        .build());
            }
        }
        criterionRepository.saveAll(criteria);
        dependencyRepository.saveAll(dependencies);

        run.setDraftJson(writeJson(draft));
        run.setProject(project);
        run.setStatus(ProjectGenerationStatus.CONFIRMED);
        runRepository.save(run);
        return response(run, savedTasks.stream().map(Task::getId).toList(), false);
    }

    private ProjectGenerationConfirmationResponse response(
            ProjectGenerationRun run,
            List<Long> taskIds,
            boolean alreadyConfirmed
    ) {
        return new ProjectGenerationConfirmationResponse(
                run.getId(),
                run.getProject().getId(),
                run.getProject().getName(),
                taskIds,
                alreadyConfirmed
        );
    }

    private void validateTicketGraph(ProjectPlanDraft draft) {
        Set<String> clientIds = new HashSet<>();
        for (PlanningTicketDraft ticket : draft.tickets()) {
            if (!clientIds.add(ticket.clientId())) {
                throw badDraft("Ticket client IDs must be unique");
            }
        }
        Map<String, List<String>> dependencies = new HashMap<>();
        for (PlanningTicketDraft ticket : draft.tickets()) {
            if (ticket.dependsOn().contains(ticket.clientId())) {
                throw badDraft("A ticket cannot depend on itself");
            }
            if (!clientIds.containsAll(ticket.dependsOn())) {
                throw badDraft("Ticket dependency references an unknown client ID");
            }
            if (ticket.dependsOn().size() != new HashSet<>(ticket.dependsOn()).size()) {
                throw badDraft("Ticket dependencies must be unique");
            }
            dependencies.put(ticket.clientId(), ticket.dependsOn());
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String clientId : clientIds) {
            visit(clientId, dependencies, visiting, visited);
        }
    }

    private void visit(
            String clientId,
            Map<String, List<String>> dependencies,
            Set<String> visiting,
            Set<String> visited
    ) {
        if (visiting.contains(clientId)) {
            throw badDraft("Ticket dependencies contain a cycle");
        }
        if (!visited.add(clientId)) {
            return;
        }
        visiting.add(clientId);
        for (String dependency : dependencies.get(clientId)) {
            visit(dependency, dependencies, visiting, visited);
        }
        visiting.remove(clientId);
    }

    private ResponseStatusException badDraft(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize confirmed project draft", exception);
        }
    }
}
