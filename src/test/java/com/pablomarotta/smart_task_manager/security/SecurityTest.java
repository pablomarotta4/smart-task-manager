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
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

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
    void localFrontendOriginCanSendCredentialedAuthRequests() throws Exception {
        mockMvc.perform(options("/api/auth/refresh")
                        .header("Origin", "http://127.0.0.1:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void protectedEndpoint_WithValidToken_ShouldReturnOk() throws Exception {
        AuthenticatedUserPrincipal userDetails = principal("testuser", "USER", 1L, 0, true);

        when(jwtTokenProvider.parseAccessToken("valid-token"))
                .thenReturn(new JwtTokenProvider.AccessTokenClaims("testuser", 1L, 0));
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);
        when(taskService.getAllTasks("testuser")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/tasks/alltasks")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_WithInactiveAccountToken_ShouldReturnUnauthorized() throws Exception {
        AuthenticatedUserPrincipal userDetails = principal("inactive", "USER", 2L, 0, false);

        when(jwtTokenProvider.parseAccessToken("inactive-token"))
                .thenReturn(new JwtTokenProvider.AccessTokenClaims("inactive", 2L, 0));
        when(userDetailsService.loadUserByUsername("inactive")).thenReturn(userDetails);

        mockMvc.perform(get("/api/tasks/alltasks")
                        .header("Authorization", "Bearer inactive-token"))
                .andExpect(status().isUnauthorized());

        verify(taskService, never()).getAllTasks(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void protectedEndpoint_WithUserIdMismatch_ShouldReturnUnauthorized() throws Exception {
        when(jwtTokenProvider.parseAccessToken("wrong-uid-token"))
                .thenReturn(new JwtTokenProvider.AccessTokenClaims("alice", 1L, 0));
        when(userDetailsService.loadUserByUsername("alice"))
                .thenReturn(principal("alice", "USER", 2L, 0, true));

        mockMvc.perform(get("/api/tasks/alltasks")
                        .header("Authorization", "Bearer wrong-uid-token"))
                .andExpect(status().isUnauthorized());

        verify(taskService, never()).getAllTasks(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void protectedEndpoint_WithSubjectUsernameMismatch_ShouldReturnUnauthorized() throws Exception {
        when(jwtTokenProvider.parseAccessToken("wrong-subject-token"))
                .thenReturn(new JwtTokenProvider.AccessTokenClaims("alice", 1L, 0));
        when(userDetailsService.loadUserByUsername("alice"))
                .thenReturn(principal("bob", "USER", 1L, 0, true));

        mockMvc.perform(get("/api/tasks/alltasks")
                        .header("Authorization", "Bearer wrong-subject-token"))
                .andExpect(status().isUnauthorized());

        verify(taskService, never()).getAllTasks(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void legacyUserCreationEndpoint_WithoutToken_ShouldReturnUnauthorized() throws Exception {
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

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).createUser(org.mockito.ArgumentMatchers.any());
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

    @Test
    void adminCannotUpdateOrDeactivateAnotherAccount() throws Exception {
        authenticate("admin-token", "admin", "ROLE_ADMIN");
        UserRequest request = validUserRequest("bob");

        mockMvc.perform(put("/api/users/bob")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/bob")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateUser(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(userService, never()).deleteUser(org.mockito.ArgumentMatchers.anyString());
    }

    private void authenticate(String token, String username, String authority) {
        AuthenticatedUserPrincipal userDetails = principal(username, authority.replace("ROLE_", ""), 1L, 0, true);
        when(jwtTokenProvider.parseAccessToken(token))
                .thenReturn(new JwtTokenProvider.AccessTokenClaims(username, 1L, 0));
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
    }

    private AuthenticatedUserPrincipal principal(
            String username,
            String role,
            Long userId,
            int authVersion,
            boolean active
    ) {
        return new AuthenticatedUserPrincipal(
                userId,
                username,
                "password",
                role,
                authVersion,
                active
        );
    }

    private UserRequest validUserRequest(String username) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setFullName("Example User");
        return request;
    }
}
