package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.ProjectMemberRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectMemberResponse;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.exception.UserNotFoundException;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectMembership;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectMembershipRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectMembershipService {

    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(Long projectId, String ownerUsername) {
        Project project = getOwnedProject(projectId, ownerUsername);
        return membershipRepository.findByProjectIdOrderByJoinedAtAsc(projectId).stream()
                .map(membership -> mapToResponse(membership, project))
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(
            Long projectId,
            ProjectMemberRequest request,
            String ownerUsername
    ) {
        Project project = getOwnedProject(projectId, ownerUsername);
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
                        .build()));
        return mapToResponse(membership, project);
    }

    @Transactional
    public void removeMember(Long projectId, Long userId, String ownerUsername) {
        Project project = getOwnedProject(projectId, ownerUsername);
        if (project.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project owner cannot be removed");
        }
        ProjectMembership membership = membershipRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project member not found"));

        taskRepository.clearAssigneeForProjectAndUser(projectId, userId);
        membershipRepository.delete(membership);
    }

    private Project getOwnedProject(Long projectId, String username) {
        return projectRepository.findByIdAndOwnerUsername(projectId, username)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));
    }

    private ProjectMemberResponse mapToResponse(ProjectMembership membership, Project project) {
        ProjectMemberResponse response = new ProjectMemberResponse();
        response.setMembershipId(membership.getId());
        response.setUserId(membership.getUser().getId());
        response.setUsername(membership.getUser().getUsername());
        response.setFullName(membership.getUser().getFullName());
        response.setOwner(project.getOwner().getId().equals(membership.getUser().getId()));
        response.setJoinedAt(membership.getJoinedAt() == null ? null : membership.getJoinedAt().toString());
        return response;
    }
}
