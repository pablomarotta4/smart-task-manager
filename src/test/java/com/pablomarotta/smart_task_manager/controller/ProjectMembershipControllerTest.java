package com.pablomarotta.smart_task_manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.ProjectMemberRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectMemberResponse;
import com.pablomarotta.smart_task_manager.service.ProjectMembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectMembershipControllerTest {

    @Mock
    private ProjectMembershipService service;

    private MockMvc mockMvc;
    private Principal principal;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectMembershipController(service)).build();
        principal = () -> "alice";
        objectMapper = new ObjectMapper();
    }

    @Test
    void listsMembersUsingAuthenticatedOwner() throws Exception {
        ProjectMemberResponse member = new ProjectMemberResponse();
        member.setMembershipId(101L);
        member.setUserId(1L);
        member.setUsername("alice");
        member.setOwner(true);
        when(service.listMembers(20L, "alice")).thenReturn(List.of(member));

        mockMvc.perform(get("/api/projects/20/members").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].owner").value(true));

        verify(service).listMembers(20L, "alice");
    }

    @Test
    void addsMemberUsingAuthenticatedOwner() throws Exception {
        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setUsername("bob");
        ProjectMemberResponse response = new ProjectMemberResponse();
        response.setMembershipId(102L);
        response.setUserId(2L);
        response.setUsername("bob");
        when(service.addMember(org.mockito.ArgumentMatchers.eq(20L), any(), org.mockito.ArgumentMatchers.eq("alice")))
                .thenReturn(response);

        mockMvc.perform(post("/api/projects/20/members")
                        .principal(principal)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob"));

        verify(service).addMember(
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.argThat(value -> "bob".equals(value.getUsername())),
                org.mockito.ArgumentMatchers.eq("alice")
        );
    }

    @Test
    void removesMemberUsingAuthenticatedOwner() throws Exception {
        mockMvc.perform(delete("/api/projects/20/members/2").principal(principal))
                .andExpect(status().isNoContent());

        verify(service).removeMember(20L, 2L, "alice");
    }
}
