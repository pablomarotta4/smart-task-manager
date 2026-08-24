package com.pablomarotta.smart_task_manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.CreateProjectInvitationRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectInvitationAcceptanceRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectInvitationAcceptanceResponse;
import com.pablomarotta.smart_task_manager.dto.ProjectInvitationResponse;
import com.pablomarotta.smart_task_manager.dto.ProjectMemberResponse;
import com.pablomarotta.smart_task_manager.dto.UpdateProjectMemberRoleRequest;
import com.pablomarotta.smart_task_manager.model.ProjectInvitationState;
import com.pablomarotta.smart_task_manager.model.ProjectRole;
import com.pablomarotta.smart_task_manager.service.ProjectInvitationService;
import com.pablomarotta.smart_task_manager.service.ProjectMembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectInvitationControllerTest {

    @Mock
    private ProjectInvitationService invitationService;
    @Mock
    private ProjectMembershipService membershipService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private Principal principal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ProjectInvitationController(invitationService),
                new ProjectMembershipController(membershipService)
        ).build();
        objectMapper = new ObjectMapper();
        principal = () -> "owner";
    }

    @Test
    void createsAnInvitationAndReturnsOnlyTheFragmentTokenUrl() throws Exception {
        UUID invitationId = UUID.randomUUID();
        when(invitationService.createInvitation(eq(20L), any(), eq("owner"))).thenReturn(invitation(
                invitationId,
                "member@example.com",
                ProjectRole.MEMBER,
                "https://tasks.example.test/invite#token=signed"
        ));

        mockMvc.perform(post("/api/projects/20/invitations")
                        .principal(principal)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateProjectInvitationRequest("member@example.com", ProjectRole.MEMBER)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationId").value(invitationId.toString()))
                .andExpect(jsonPath("$.inviteUrl").value("https://tasks.example.test/invite#token=signed"));

        verify(invitationService).createInvitation(eq(20L), any(), eq("owner"));
    }

    @Test
    void acceptsTokenFromThePostBodyWithoutProjectOrRoleFields() throws Exception {
        ProjectMemberResponse member = new ProjectMemberResponse();
        member.setMembershipId(51L);
        member.setRole(ProjectRole.MEMBER);
        when(invitationService.acceptInvitation("signed", "owner"))
                .thenReturn(new ProjectInvitationAcceptanceResponse(20L, member));

        mockMvc.perform(post("/api/project-invitations/accept")
                        .principal(principal)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ProjectInvitationAcceptanceRequest("signed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(20))
                .andExpect(jsonPath("$.member.role").value("MEMBER"));

        verify(invitationService).acceptInvitation("signed", "owner");
    }

    @Test
    void revokesAndChangesMemberRolesThroughTheAuthenticatedActor() throws Exception {
        UUID invitationId = UUID.randomUUID();
        ProjectMemberResponse member = new ProjectMemberResponse();
        member.setMembershipId(51L);
        member.setRole(ProjectRole.MANAGER);
        when(membershipService.changeRole(20L, 8L, new UpdateProjectMemberRoleRequest(ProjectRole.MANAGER), "owner"))
                .thenReturn(member);

        mockMvc.perform(delete("/api/projects/20/invitations/{invitationId}", invitationId).principal(principal))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/projects/20/members/8/role")
                        .principal(principal)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateProjectMemberRoleRequest(ProjectRole.MANAGER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));

        verify(invitationService).revokeInvitation(20L, invitationId, "owner");
    }

    private ProjectInvitationResponse invitation(UUID invitationId, String email, ProjectRole role, String inviteUrl) {
        return new ProjectInvitationResponse(
                invitationId,
                20L,
                email,
                role,
                ProjectInvitationState.PENDING,
                Instant.parse("2026-08-19T18:00:00Z"),
                inviteUrl
        );
    }
}
