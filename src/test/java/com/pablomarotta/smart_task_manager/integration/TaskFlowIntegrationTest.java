package com.pablomarotta.smart_task_manager.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.*;
import com.pablomarotta.smart_task_manager.model.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TaskFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testFullTaskLifecycle() throws Exception {
        // 1. Registrar usuario y obtener token
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setEmail("testuser@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setFullName("Test User");

        MvcResult authResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(authResult.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(authResponse.getUser().getId());
        String token = authResponse.getToken();

        // 2. Crear proyecto
        ProjectRequest projectRequest = new ProjectRequest();
        projectRequest.setName("Integration Test Project");
        projectRequest.setUsername(authResponse.getUser().getUsername());

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        ProjectResponse projectResponse = objectMapper.readValue(projectResult.getResponse().getContentAsString(), ProjectResponse.class);
        assertNotNull(projectResponse.getId());

        // 3. Crear task
        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setTitle("Integration Test Task");
        taskRequest.setDescription("Task description");
        taskRequest.setStatus(Status.TODO);
        taskRequest.setProjectId(projectResponse.getId());
        taskRequest.setAssigneeId(authResponse.getUser().getId());

        MvcResult taskResult = mockMvc.perform(post("/api/tasks/newtask")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TaskResponse taskResponse = objectMapper.readValue(taskResult.getResponse().getContentAsString(), TaskResponse.class);
        assertNotNull(taskResponse.getId());
        assertEquals(Status.TODO, taskResponse.getStatus());

        // 4. Cambiar de estado (TODO -> IN_PROGRESS)
        MvcResult updateStatusResult = mockMvc.perform(patch("/api/tasks/" + taskResponse.getId() + "/status")
                .header("Authorization", "Bearer " + token)
                .param("status", Status.IN_PROGRESS.name()))
                .andExpect(status().isOk())
                .andReturn();

        TaskResponse updatedTaskResponse = objectMapper.readValue(updateStatusResult.getResponse().getContentAsString(), TaskResponse.class);
        assertEquals(Status.IN_PROGRESS, updatedTaskResponse.getStatus());

        // 5. Cerrar task (IN_PROGRESS -> DONE)
        MvcResult closeTaskResult = mockMvc.perform(patch("/api/tasks/" + taskResponse.getId() + "/status")
                .header("Authorization", "Bearer " + token)
                .param("status", Status.DONE.name()))
                .andExpect(status().isOk())
                .andReturn();

        TaskResponse closedTaskResponse = objectMapper.readValue(closeTaskResult.getResponse().getContentAsString(), TaskResponse.class);
        assertEquals(Status.DONE, closedTaskResponse.getStatus());
    }
}
