package com.pablomarotta.smart_task_manager.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.*;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.User;
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
        // 1. Crear usuario
        UserRequest userRequest = new UserRequest();
        userRequest.setUsername("testuser");
        userRequest.setEmail("testuser@example.com");
        userRequest.setPassword("password123");
        userRequest.setFullName("Test User");

        MvcResult userResult = mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse userResponse = objectMapper.readValue(userResult.getResponse().getContentAsString(), UserResponse.class);
        assertNotNull(userResponse.getId());

        // Necesitamos el objeto User para el ProjectRequest según el DTO actual
        User owner = User.builder()
                .id(userResponse.getId())
                .username(userResponse.getUsername())
                .email(userResponse.getEmail())
                .fullName(userResponse.getFullName())
                .active(userResponse.getActive())
                .build();

        // 2. Crear proyecto
        ProjectRequest projectRequest = new ProjectRequest();
        projectRequest.setName("Integration Test Project");
        projectRequest.setOwner(owner);

        MvcResult projectResult = mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
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
        taskRequest.setAssigneeId(userResponse.getId());

        MvcResult taskResult = mockMvc.perform(post("/tasks/newtask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TaskResponse taskResponse = objectMapper.readValue(taskResult.getResponse().getContentAsString(), TaskResponse.class);
        assertNotNull(taskResponse.getId());
        assertEquals(Status.TODO, taskResponse.getStatus());

        // 4. Cambiar de estado (TODO -> IN_PROGRESS)
        MvcResult updateStatusResult = mockMvc.perform(patch("/tasks/" + taskResponse.getId() + "/status")
                .param("status", Status.IN_PROGRESS.name()))
                .andExpect(status().isOk())
                .andReturn();

        TaskResponse updatedTaskResponse = objectMapper.readValue(updateStatusResult.getResponse().getContentAsString(), TaskResponse.class);
        assertEquals(Status.IN_PROGRESS, updatedTaskResponse.getStatus());

        // 5. Cerrar task (IN_PROGRESS -> DONE)
        MvcResult closeTaskResult = mockMvc.perform(patch("/tasks/" + taskResponse.getId() + "/status")
                .param("status", Status.DONE.name()))
                .andExpect(status().isOk())
                .andReturn();

        TaskResponse closedTaskResponse = objectMapper.readValue(closeTaskResult.getResponse().getContentAsString(), TaskResponse.class);
        assertEquals(Status.DONE, closedTaskResponse.getStatus());
    }
}
