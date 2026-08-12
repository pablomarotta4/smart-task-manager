package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.ProjectMemberRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectMemberResponse;
import com.pablomarotta.smart_task_manager.exception.UserNotFoundException;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectMembership;
import com.pablomarotta.smart_task_manager.model.ProjectRole;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectMembershipRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectMembershipService {

    private final ProjectMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ProjectAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(Long projectId, String ownerUsername) {
        accessPolicy.requireMember(projectId, ownerUsername);
        return membershipRepository.findByProjectIdOrderByJoinedAtAsc(projectId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(
            Long projectId,
            ProjectMemberRequest request,
            String ownerUsername
    ) {
        Project project = accessPolicy.requireManager(projectId, ownerUsername).getProject();
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with username: " + request.getUsername().trim()
                ));
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inactive users cannot join a project");
        }

        ProjectMembership membership = membershipRepository
                .findByProjectIdAndUserId(projectId, user.getId())
                .orElseGet(() -> membershipRepository.save(ProjectMembership.builder()
                        .project(project)
                        .user(user)
                        .role(ProjectRole.MEMBER)
                        .build()));
        return mapToResponse(membership);
    }

    @Transactional
    public void removeMember(Long projectId, Long userId, String ownerUsername) {
        ProjectMembership requester = accessPolicy.requireManager(projectId, ownerUsername);
        ProjectMembership membership = membershipRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project member not found"));
        if (membership.getRole() == ProjectRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project owner cannot be removed");
        }
        if (requester.getRole() == ProjectRole.MANAGER && membership.getRole() != ProjectRole.MEMBER) {
            throw new AccessDeniedException("Managers cannot remove project managers");
        }

        taskRepository.clearAssigneeForProjectAndUser(projectId, userId);
        membershipRepository.delete(membership);
    }

    private ProjectMemberResponse mapToResponse(ProjectMembership membership) {
        ProjectMemberResponse response = new ProjectMemberResponse();
        response.setMembershipId(membership.getId());
        response.setUserId(membership.getUser().getId());
        response.setUsername(membership.getUser().getUsername());
        response.setFullName(membership.getUser().getFullName());
        response.setOwner(membership.getRole() == ProjectRole.OWNER);
        response.setRole(membership.getRole());
        response.setJoinedAt(membership.getJoinedAt() == null ? null : membership.getJoinedAt().toString());
        return response;
    }
}
