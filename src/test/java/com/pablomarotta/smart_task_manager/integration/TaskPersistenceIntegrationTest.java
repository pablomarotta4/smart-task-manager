package com.pablomarotta.smart_task_manager.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.AuthResponse;
import com.pablomarotta.smart_task_manager.dto.ProjectRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectResponse;
import com.pablomarotta.smart_task_manager.dto.RegisterRequest;
import com.pablomarotta.smart_task_manager.dto.TaskRequest;
import com.pablomarotta.smart_task_manager.dto.TaskResponse;
import com.pablomarotta.smart_task_manager.model.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class TaskPersistenceIntegrationTest extends PostgresIntegrationTest {

    private final MockMvc mockMvc;

    private final ObjectMapper objectMapper;

    @Autowired
    TaskPersistenceIntegrationTest(DataSource dataSource, MockMvc mockMvc, ObjectMapper objectMapper) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void createsAndRetrievesTask() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("persistence_test_user");
        registerRequest.setEmail("persistence@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setFullName("Persistence Test User");

        MvcResult authResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                authResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        assertNotNull(authResponse.getUser().getId());
        String token = authResponse.getToken();

        ProjectRequest projectRequest = new ProjectRequest();
        projectRequest.setName("Persistence Test Project");
        projectRequest.setUsername(authResponse.getUser().getUsername());

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        ProjectResponse projectResponse = objectMapper.readValue(
                projectResult.getResponse().getContentAsString(),
                ProjectResponse.class
        );
        assertNotNull(projectResponse.getId());

        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setTitle("Fix critical authentication bug");
        taskRequest.setDescription("Users are unable to login after password reset. " +
                "The session token is not being properly regenerated. " +
                "This is blocking production deployment and needs immediate attention.");
        taskRequest.setStatus(Status.TODO);
        taskRequest.setProjectId(projectResponse.getId());

        MvcResult taskResult = mockMvc.perform(post("/api/tasks/newtask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TaskResponse taskResponse = objectMapper.readValue(
                taskResult.getResponse().getContentAsString(),
                TaskResponse.class
        );

        assertNotNull(taskResponse.getId());
        assertEquals("Fix critical authentication bug", taskResponse.getTitle());
        assertEquals(Status.TODO, taskResponse.getStatus());
        assertEquals(projectResponse.getId(), taskResponse.getProjectId());

        MvcResult getTaskResult = mockMvc.perform(get("/api/tasks/" + taskResponse.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        TaskResponse retrievedTask = objectMapper.readValue(
                getTaskResult.getResponse().getContentAsString(),
                TaskResponse.class
        );

        assertNotNull(retrievedTask);
        assertEquals(taskResponse.getId(), retrievedTask.getId());
        assertEquals(taskResponse.getTitle(), retrievedTask.getTitle());
        assertEquals(taskResponse.getDescription(), retrievedTask.getDescription());
        assertEquals(taskResponse.getStatus(), retrievedTask.getStatus());
        assertEquals(taskResponse.getProjectId(), retrievedTask.getProjectId());
    }

    @Test
    void createsTaskWithFeatureDescription() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("feature_test_user");
        registerRequest.setEmail("featuretest@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setFullName("Feature Test User");

        MvcResult authResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                authResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String token = authResponse.getToken();

        ProjectRequest projectRequest = new ProjectRequest();
        projectRequest.setName("Feature Test Project");
        projectRequest.setUsername(authResponse.getUser().getUsername());

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        ProjectResponse projectResponse = objectMapper.readValue(
                projectResult.getResponse().getContentAsString(),
                ProjectResponse.class
        );

        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setTitle("Add dark mode theme");
        taskRequest.setDescription("Implement a dark mode theme toggle for better user experience. " +
                "Should include persistent user preference storage and smooth transitions between themes.");
        taskRequest.setStatus(Status.TODO);
        taskRequest.setProjectId(projectResponse.getId());

        MvcResult taskResult = mockMvc.perform(post("/api/tasks/newtask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TaskResponse taskResponse = objectMapper.readValue(
                taskResult.getResponse().getContentAsString(),
                TaskResponse.class
        );

        assertNotNull(taskResponse.getId());
        assertEquals("Add dark mode theme", taskResponse.getTitle());
        assertEquals(Status.TODO, taskResponse.getStatus());
        assertEquals(projectResponse.getId(), taskResponse.getProjectId());
    }
}
