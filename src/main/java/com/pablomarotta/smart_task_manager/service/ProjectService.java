package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.ProjectRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectResponse;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.exception.UserNotFoundException;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public ProjectResponse createProject(ProjectRequest projectRequest, String username) {
        try {
            User owner = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

            Project project = Project.builder()
                    .name(projectRequest.getName())
                    .owner(owner)
                    .build();
            Project savedProject = projectRepository.save(project);
            return mapToResponse(savedProject, 0);
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error creating project: " + e.getMessage());
        }
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects(String username) {
        Map<Long, Long> taskCounts = taskRepository.countTasksByProject().stream()
                .collect(Collectors.toMap(
                        TaskRepository.ProjectTaskCount::getProjectId,
                        TaskRepository.ProjectTaskCount::getTaskCount
                ));

        return projectRepository.findByOwnerUsernameOrderByCreatedAtDesc(username).stream()
                .map(project -> mapToResponse(project, taskCounts.getOrDefault(project.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long id, String username) {
        Project project = projectRepository.findByIdAndOwnerUsername(id, username)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + id));
        return mapToResponse(project, taskRepository.countByProjectId(id));
    }

    private ProjectResponse mapToResponse(Project project, long taskCount) {
        ProjectResponse projectResponse = new ProjectResponse();
        projectResponse.setId(project.getId());
        projectResponse.setName(project.getName());
        projectResponse.setObjective(project.getObjective());
        projectResponse.setTaskCount(taskCount);
        projectResponse.setOwnerId(project.getOwner().getId());
        projectResponse.setOwnerUsername(project.getOwner().getUsername());
        projectResponse.setCreatedAt(project.getCreatedAt() != null
                ? project.getCreatedAt().toString()
                : null);

        return projectResponse;
    }
}
