package com.pablomarotta.smart_task_manager.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.*;
import com.pablomarotta.smart_task_manager.model.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "ai.ollama.enabled=true",
    "ai.ollama.base-url=http://localhost:11434",
    "ai.ollama.model=llama3.2"
})
public class TaskAIIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testCreateTaskWithAIClassification() throws Exception {
        // 1. Crear usuario
        UserRequest userRequest = new UserRequest();
        userRequest.setUsername("aitest_user");
        userRequest.setEmail("aitest@example.com");
        userRequest.setPassword("password123");
        userRequest.setFullName("AI Test User");

        MvcResult userResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse userResponse = objectMapper.readValue(
                userResult.getResponse().getContentAsString(),
                UserResponse.class
        );
        assertNotNull(userResponse.getId());

        // 2. Crear proyecto
        ProjectRequest projectRequest = new ProjectRequest();
        projectRequest.setName("AI Test Project");
        projectRequest.setUsername(userResponse.getUsername());

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        ProjectResponse projectResponse = objectMapper.readValue(
                projectResult.getResponse().getContentAsString(),
                ProjectResponse.class
        );
        assertNotNull(projectResponse.getId());

        // 3. Crear tarea con descripción detallada para que la IA la clasifique
        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setTitle("Fix critical authentication bug");
        taskRequest.setDescription("Users are unable to login after password reset. " +
                "The session token is not being properly regenerated. " +
                "This is blocking production deployment and needs immediate attention.");
        taskRequest.setStatus(Status.TODO);
        taskRequest.setProjectId(projectResponse.getId());

        MvcResult taskResult = mockMvc.perform(post("/tasks/newtask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TaskResponse taskResponse = objectMapper.readValue(
                taskResult.getResponse().getContentAsString(),
                TaskResponse.class
        );

        // 4. Verificar que la tarea fue creada
        assertNotNull(taskResponse.getId());
        assertEquals("Fix critical authentication bug", taskResponse.getTitle());
        assertEquals(Status.TODO, taskResponse.getStatus());
        assertEquals(projectResponse.getId(), taskResponse.getProjectId());

        // 5. Verificar que la IA clasificó la tarea
        // Nota: Los campos AI no están en TaskResponse actualmente,
        // pero deberían estar para validar la clasificación
        System.out.println("Task created with ID: " + taskResponse.getId());
        System.out.println("AI classification should have populated:");
        System.out.println("- aiPriority (expected: HIGH or URGENT)");
        System.out.println("- aiCategory (expected: BUG)");
        System.out.println("- aiSuggestedDueDays (expected: 1-3 days)");
        System.out.println("- aiSummary (expected: concise summary)");

        // 6. Obtener la tarea completa para verificar campos AI
        MvcResult getTaskResult = mockMvc.perform(get("/tasks/" + taskResponse.getId()))
                .andExpect(status().isOk())
                .andReturn();

        TaskResponse retrievedTask = objectMapper.readValue(
                getTaskResult.getResponse().getContentAsString(),
                TaskResponse.class
        );

        assertNotNull(retrievedTask);
        assertEquals(taskResponse.getId(), retrievedTask.getId());

        // TODO: Descomentar cuando TaskResponse incluya campos AI
        // assertNotNull(retrievedTask.getAiPriority(), "AI should have classified priority");
        // assertNotNull(retrievedTask.getAiCategory(), "AI should have classified category");
        // assertNotNull(retrievedTask.getAiSuggestedDueDays(), "AI should have suggested due days");
        // assertNotNull(retrievedTask.getAiSummary(), "AI should have generated summary");

        // assertTrue(
        //     retrievedTask.getAiPriority().equals("HIGH") || retrievedTask.getAiPriority().equals("URGENT"),
        //     "Critical bug should be HIGH or URGENT priority"
        // );
        // assertEquals("BUG", retrievedTask.getAiCategory(), "Should be classified as BUG");
        // assertTrue(retrievedTask.getAiSuggestedDueDays() <= 3, "Critical bug should be completed within 3 days");
    }

    @Test
    public void testCreateTaskWithAI_FeatureRequest() throws Exception {
        // 1. Crear usuario
        UserRequest userRequest = new UserRequest();
        userRequest.setUsername("feature_test_user");
        userRequest.setEmail("featuretest@example.com");
        userRequest.setPassword("password123");
        userRequest.setFullName("Feature Test User");

        MvcResult userResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse userResponse = objectMapper.readValue(
                userResult.getResponse().getContentAsString(),
                UserResponse.class
        );

        // 2. Crear proyecto
        ProjectRequest projectRequest = new ProjectRequest();
        projectRequest.setName("Feature Test Project");
        projectRequest.setUsername(userResponse.getUsername());

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        ProjectResponse projectResponse = objectMapper.readValue(
                projectResult.getResponse().getContentAsString(),
                ProjectResponse.class
        );

        // 3. Crear tarea tipo FEATURE
        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setTitle("Add dark mode theme");
        taskRequest.setDescription("Implement a dark mode theme toggle for better user experience. " +
                "Should include persistent user preference storage and smooth transitions between themes.");
        taskRequest.setStatus(Status.TODO);
        taskRequest.setProjectId(projectResponse.getId());

        MvcResult taskResult = mockMvc.perform(post("/tasks/newtask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TaskResponse taskResponse = objectMapper.readValue(
                taskResult.getResponse().getContentAsString(),
                TaskResponse.class
        );

        assertNotNull(taskResponse.getId());
        assertEquals("Add dark mode theme", taskResponse.getTitle());

        // TODO: Verificar clasificación AI cuando TaskResponse incluya campos
        // assertNotNull(taskResponse.getAiCategory());
        // assertEquals("FEATURE", taskResponse.getAiCategory());
        // assertTrue(taskResponse.getAiSuggestedDueDays() >= 5, "Feature should take several days");
    }
}
