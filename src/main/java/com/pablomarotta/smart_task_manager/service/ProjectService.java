package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.ProjectRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectResponse;
import com.pablomarotta.smart_task_manager.exception.UserNotFoundException;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectMembership;
import com.pablomarotta.smart_task_manager.model.ProjectRole;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectMembershipRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final ProjectAccessPolicy accessPolicy;

    @Transactional
    public ProjectResponse createProject(ProjectRequest projectRequest, String username) {
        User owner = getUserByUsername(username);
        Project project = Project.builder()
                .name(projectRequest.getName().trim())
                .objective(normalizeObjective(projectRequest.getObjective()))
                .owner(owner)
                .build();
        Project savedProject = projectRepository.save(project);
        membershipRepository.save(ProjectMembership.builder()
                .project(savedProject)
                .user(owner)
                .role(ProjectRole.OWNER)
                .build());
        return mapToResponse(savedProject, 0, ProjectRole.OWNER);
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects(String username) {
        Map<Long, Long> taskCounts = taskRepository.countTasksByProjectMemberUsername(username).stream()
                .collect(Collectors.toMap(
                        TaskRepository.ProjectTaskCount::getProjectId,
                        TaskRepository.ProjectTaskCount::getTaskCount
                ));

        return membershipRepository.findByUserUsernameOrderByProjectCreatedAtDesc(username).stream()
                .map(membership -> mapToResponse(
                        membership.getProject(),
                        taskCounts.getOrDefault(membership.getProject().getId(), 0L),
                        membership.getRole()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long id, String username) {
        ProjectMembership membership = accessPolicy.requireMember(id, username);
        return mapToResponse(membership.getProject(), taskRepository.countByProjectId(id), membership.getRole());
    }

    @Transactional
    public ProjectResponse updateProject(Long id, ProjectRequest projectRequest, String username) {
        Project project = accessPolicy.requireOwner(id, username).getProject();
        project.setName(projectRequest.getName().trim());
        project.setObjective(normalizeObjective(projectRequest.getObjective()));
        Project savedProject = projectRepository.save(project);
        return mapToResponse(savedProject, taskRepository.countByProjectId(id), ProjectRole.OWNER);
    }

    @Transactional
    public void deleteProject(Long id, String username) {
        Project project = accessPolicy.requireOwner(id, username).getProject();
        projectRepository.delete(project);
    }

    private String normalizeObjective(String objective) {
        if (objective == null || objective.isBlank()) {
            return null;
        }
        return objective.trim();
    }

    private ProjectResponse mapToResponse(Project project, long taskCount, ProjectRole currentUserRole) {
        ProjectResponse projectResponse = new ProjectResponse();
        projectResponse.setId(project.getId());
        projectResponse.setName(project.getName());
        projectResponse.setObjective(project.getObjective());
        projectResponse.setTaskCount(taskCount);
        projectResponse.setOwnerId(project.getOwner().getId());
        projectResponse.setOwnerUsername(project.getOwner().getUsername());
        projectResponse.setCurrentUserRole(currentUserRole);
        projectResponse.setCreatedAt(project.getCreatedAt() != null
                ? project.getCreatedAt().toString()
                : null);

        return projectResponse;
    }
}
