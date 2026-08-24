package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectMembership;
import com.pablomarotta.smart_task_manager.model.ProjectRole;
import com.pablomarotta.smart_task_manager.model.Role;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectMembershipRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessPolicyTest {

    private static final Long PROJECT_ID = 20L;
    private static final Long TASK_ID = 101L;

    @Mock
    private ProjectMembershipRepository membershipRepository;
    @Mock
    private TaskRepository taskRepository;
    @InjectMocks
    private ProjectAccessPolicy accessPolicy;

    private Project project;
    private Task task;

    @BeforeEach
    void setUp() {
        User owner = user(1L, "alice", Role.USER);
        project = Project.builder().id(PROJECT_ID).name("Release plan").owner(owner).build();
        task = Task.builder().id(TASK_ID).project(project).title("Ship first release").build();
    }

    @Test
    void nonmemberCannotUseGlobalAdminRoleAsAProjectBypass() {
        when(membershipRepository.findByProjectIdAndUserUsername(PROJECT_ID, "admin"))
                .thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> accessPolicy.requireMember(PROJECT_ID, "admin"));
    }

    @Test
    void memberCanViewProjectButCannotManageIt() {
        ProjectMembership membership = membership(user(2L, "bob", Role.USER), ProjectRole.MEMBER);
        when(membershipRepository.findByProjectIdAndUserUsername(PROJECT_ID, "bob"))
                .thenReturn(Optional.of(membership));

        assertSame(membership, accessPolicy.requireMember(PROJECT_ID, "bob"));
        assertThrows(AccessDeniedException.class, () -> accessPolicy.requireManager(PROJECT_ID, "bob"));
    }

    @Test
    void managerCanManageProject() {
        ProjectMembership membership = membership(user(3L, "carol", Role.USER), ProjectRole.MANAGER);
        when(membershipRepository.findByProjectIdAndUserUsername(PROJECT_ID, "carol"))
                .thenReturn(Optional.of(membership));

        assertSame(membership, accessPolicy.requireManager(PROJECT_ID, "carol"));
    }

    @Test
    void memberCanEditOnlyTheirAssignedTask() {
        User member = user(2L, "bob", Role.USER);
        task.setAssignee(member);
        ProjectMembership membership = membership(member, ProjectRole.MEMBER);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(membershipRepository.findByProjectIdAndUserUsername(PROJECT_ID, "bob"))
                .thenReturn(Optional.of(membership));

        ProjectAccessPolicy.TaskAccess access = accessPolicy.requireTaskEditor(TASK_ID, "bob");

        assertEquals(task, access.task());
        assertEquals(ProjectRole.MEMBER, access.membership().getRole());
    }

    @Test
    void memberCannotEditAnotherMembersTask() {
        task.setAssignee(user(3L, "carol", Role.USER));
        ProjectMembership membership = membership(user(2L, "bob", Role.USER), ProjectRole.MEMBER);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(membershipRepository.findByProjectIdAndUserUsername(PROJECT_ID, "bob"))
                .thenReturn(Optional.of(membership));

        assertThrows(AccessDeniedException.class, () -> accessPolicy.requireTaskEditor(TASK_ID, "bob"));
    }

    private User user(Long id, String username, Role role) {
        return User.builder().id(id).username(username).role(role).active(true).build();
    }

    private ProjectMembership membership(User user, ProjectRole role) {
        return ProjectMembership.builder().project(project).user(user).role(role).build();
    }
}
