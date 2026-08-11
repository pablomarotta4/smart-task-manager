package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.ProjectMemberRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectMemberResponse;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectMembership;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectMembershipRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMembershipServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private ProjectMembershipService service;

    private User owner;
    private User member;
    private Project project;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).username("alice").fullName("Alice Owner").active(true).build();
        member = User.builder().id(2L).username("bob").fullName("Bob Builder").active(true).build();
        project = Project.builder().id(20L).name("Release plan").owner(owner).build();
    }

    @Test
    void listMembersReturnsOnlyAnOwnedProjectsParticipants() {
        ProjectMembership ownerMembership = membership(101L, owner, LocalDateTime.now().minusDays(2));
        ProjectMembership memberMembership = membership(102L, member, LocalDateTime.now().minusDays(1));
        when(projectRepository.findByIdAndOwnerUsername(20L, "alice")).thenReturn(Optional.of(project));
        when(membershipRepository.findByProjectIdOrderByJoinedAtAsc(20L))
                .thenReturn(List.of(ownerMembership, memberMembership));

        List<ProjectMemberResponse> response = service.listMembers(20L, "alice");

        assertEquals(List.of("alice", "bob"), response.stream().map(ProjectMemberResponse::getUsername).toList());
        assertEquals(true, response.getFirst().isOwner());
        assertEquals(false, response.getLast().isOwner());
    }

    @Test
    void foreignOwnerCannotInspectMembership() {
        when(projectRepository.findByIdAndOwnerUsername(20L, "mallory")).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> service.listMembers(20L, "mallory"));

        verify(membershipRepository, never()).findByProjectIdOrderByJoinedAtAsc(any());
    }

    @Test
    void addMemberPersistsAnActiveUser() {
        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setUsername("bob");
        when(projectRepository.findByIdAndOwnerUsername(20L, "alice")).thenReturn(Optional.of(project));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(member));
        when(membershipRepository.findByProjectIdAndUserId(20L, 2L)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(ProjectMembership.class))).thenAnswer(invocation -> {
            ProjectMembership saved = invocation.getArgument(0);
            saved.setId(102L);
            saved.setJoinedAt(LocalDateTime.now());
            return saved;
        });

        ProjectMemberResponse response = service.addMember(20L, request, "alice");

        assertEquals("bob", response.getUsername());
        assertEquals(false, response.isOwner());
        verify(membershipRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getProject() == project && saved.getUser() == member
        ));
    }

    @Test
    void addMemberRejectsInactiveUser() {
        member.setActive(false);
        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setUsername("bob");
        when(projectRepository.findByIdAndOwnerUsername(20L, "alice")).thenReturn(Optional.of(project));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(member));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.addMember(20L, request, "alice")
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void addingExistingMemberIsIdempotent() {
        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setUsername("bob");
        ProjectMembership existing = membership(102L, member, LocalDateTime.now());
        when(projectRepository.findByIdAndOwnerUsername(20L, "alice")).thenReturn(Optional.of(project));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(member));
        when(membershipRepository.findByProjectIdAndUserId(20L, 2L))
                .thenReturn(Optional.of(existing));

        ProjectMemberResponse response = service.addMember(20L, request, "alice");

        assertEquals(102L, response.getMembershipId());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void removeMemberClearsProjectAssignmentsBeforeMembership() {
        ProjectMembership existing = membership(102L, member, LocalDateTime.now());
        when(projectRepository.findByIdAndOwnerUsername(20L, "alice")).thenReturn(Optional.of(project));
        when(membershipRepository.findByProjectIdAndUserId(20L, 2L))
                .thenReturn(Optional.of(existing));

        service.removeMember(20L, 2L, "alice");

        verify(taskRepository).clearAssigneeForProjectAndUser(20L, 2L);
        verify(membershipRepository).delete(existing);
    }

    @Test
    void ownerCannotBeRemovedFromTheirProject() {
        when(projectRepository.findByIdAndOwnerUsername(20L, "alice")).thenReturn(Optional.of(project));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.removeMember(20L, 1L, "alice")
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(taskRepository, never()).clearAssigneeForProjectAndUser(any(), any());
        verify(membershipRepository, never()).delete(any());
    }

    private ProjectMembership membership(Long id, User user, LocalDateTime joinedAt) {
        return ProjectMembership.builder()
                .id(id)
                .project(project)
                .user(user)
                .joinedAt(joinedAt)
                .build();
    }
}
