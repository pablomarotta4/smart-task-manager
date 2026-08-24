package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.exception.TaskNotFoundException;
import com.pablomarotta.smart_task_manager.model.ProjectMembership;
import com.pablomarotta.smart_task_manager.model.ProjectRole;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.repository.ProjectMembershipRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectAccessPolicy {

    private final ProjectMembershipRepository membershipRepository;
    private final TaskRepository taskRepository;

    public ProjectMembership requireMember(Long projectId, String username) {
        return membershipRepository.findByProjectIdAndUserUsername(projectId, username)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));
    }

    public ProjectMembership requireManager(Long projectId, String username) {
        ProjectMembership membership = requireMember(projectId, username);
        if (!membership.getRole().canManageProject()) {
            throw new AccessDeniedException("Project manager permission is required");
        }
        return membership;
    }

    public ProjectMembership requireOwner(Long projectId, String username) {
        ProjectMembership membership = requireMember(projectId, username);
        if (membership.getRole() != ProjectRole.OWNER) {
            throw new AccessDeniedException("Project owner permission is required");
        }
        return membership;
    }

    public Task requireTaskViewer(Long taskId, String username) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));
        requireMember(task.getProject().getId(), username);
        return task;
    }

    public TaskAccess requireTaskEditor(Long taskId, String username) {
        Task task = requireTaskViewer(taskId, username);
        ProjectMembership membership = requireMember(task.getProject().getId(), username);
        if (membership.getRole().canManageProject()) {
            return new TaskAccess(task, membership);
        }
        if (task.getAssignee() != null && task.getAssignee().getUsername().equals(username)) {
            return new TaskAccess(task, membership);
        }
        throw new AccessDeniedException("Members can edit only their assigned tasks");
    }

    public record TaskAccess(Task task, ProjectMembership membership) {
    }
}
