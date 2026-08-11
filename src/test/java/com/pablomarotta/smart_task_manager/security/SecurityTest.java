package com.pablomarotta.smart_task_manager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.config.SecurityConfig;
import com.pablomarotta.smart_task_manager.controller.TaskController;
import com.pablomarotta.smart_task_manager.controller.UserController;
import com.pablomarotta.smart_task_manager.dto.UserRequest;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.service.TaskService;
import com.pablomarotta.smart_task_manager.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TaskController.class, UserController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void protectedEndpoint_WithoutToken_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/tasks/alltasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_WithValidToken_ShouldReturnOk() throws Exception {
        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities(Collections.emptyList())
                .build();

        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("valid-token")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);
        when(taskService.getAllTasks("testuser")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/tasks/alltasks")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }

    @Test
    void publicEndpoint_WithoutToken_ShouldReturnCreated() throws Exception {
        UserRequest userRequest = new UserRequest();
        userRequest.setUsername("newuser");
        userRequest.setEmail("new@example.com");
        userRequest.setPassword("password");
        userRequest.setFullName("New User");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setUsername("newuser");
        userResponse.setEmail("new@example.com");
        userResponse.setFullName("New User");
        userResponse.setRole("USER");

        when(userService.createUser(org.mockito.ArgumentMatchers.any())).thenReturn(userResponse);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated());
    }

    @Test
    void ordinaryUserCannotListAllUsers() throws Exception {
        authenticate("user-token", "alice", "ROLE_USER");

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());

        verify(userService, never()).getAllUsers();
    }

    @Test
    void adminCanListAllUsers() throws Exception {
        authenticate("admin-token", "admin", "ROLE_ADMIN");
        when(userService.getAllUsers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void userCanReadOwnProfileButNotAnotherProfile() throws Exception {
        authenticate("user-token", "alice", "ROLE_USER");
        when(userService.getUserByUsername("alice")).thenReturn(new UserResponse());

        mockMvc.perform(get("/api/users/username/alice")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/username/bob")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());

        verify(userService).getUserByUsername("alice");
        verify(userService, never()).getUserByUsername("bob");
    }

    @Test
    void userCannotUpdateOrDeactivateAnotherAccount() throws Exception {
        authenticate("user-token", "alice", "ROLE_USER");
        UserRequest request = validUserRequest("bob");

        mockMvc.perform(put("/api/users/bob")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/bob")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateUser(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(userService, never()).deleteUser(org.mockito.ArgumentMatchers.anyString());
    }

    private void authenticate(String token, String username, String authority) {
        UserDetails userDetails = User.withUsername(username)
                .password("password")
                .authorities(authority)
                .build();
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(username);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
    }

    private UserRequest validUserRequest(String username) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setFullName("Example User");
        return request;
    }
}
