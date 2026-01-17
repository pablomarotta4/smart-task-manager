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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        when(taskService.getAllTasks()).thenReturn(Collections.emptyList());

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
}
