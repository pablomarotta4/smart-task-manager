package com.pablomarotta.smart_task_manager.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.*;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class FullFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    public void setup() {
        taskRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testCompleteFlowWithDeduplication() throws Exception {
        // === 1) Crear usuario ===
        UserRequest userRequest = new UserRequest();
        userRequest.setUsername("flowuser");
        userRequest.setEmail("flowuser@example.com");
        userRequest.setPassword("password123");
        userRequest.setFullName("Flow User");

        MvcResult userResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse userResponse = objectMapper.readValue(userResult.getResponse().getContentAsString(), UserResponse.class);
        assertNotNull(userResponse.getId());
        Long userId = userResponse.getId();

        // === PRUEBA DE DEDUPLICACIÓN ===
        // Intentar crear el mismo usuario otra vez
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isConflict());

        // Intentar crear usuario con mismo email pero distinto username
        UserRequest userRequestSameEmail = new UserRequest();
        userRequestSameEmail.setUsername("otheruser");
        userRequestSameEmail.setEmail("flowuser@example.com");
        userRequestSameEmail.setPassword("password123");
        userRequestSameEmail.setFullName("Other User");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequestSameEmail)))
                .andExpect(status().isConflict());

        // === 2) Crear proyecto para ese usuario ===
        User owner = User.builder()
                .id(userId)
                .username("flowuser")
                .email("flowuser@example.com")
                .fullName("Flow User")
                .active(true)
                .build();

        ProjectRequest projectRequest = new ProjectRequest();
        projectRequest.setName("Flow Project");
        projectRequest.setOwner(owner);

        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        ProjectResponse projectResponse = objectMapper.readValue(projectResult.getResponse().getContentAsString(), ProjectResponse.class);
        assertNotNull(projectResponse.getId());
        Long projectId = projectResponse.getId();

        // === 3) Crear tres tasks en el proyecto ===

        // Task 1
        TaskRequest task1Request = new TaskRequest();
        task1Request.setTitle("Flow Task 1");
        task1Request.setDescription("Primera task del flujo");
        task1Request.setStatus(Status.TODO);
        task1Request.setProjectId(projectId);
        task1Request.setAssigneeId(userId);

        MvcResult task1Result = mockMvc.perform(post("/tasks/newtask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task1Request)))
                .andExpect(status().isCreated())
                .andReturn();
        TaskResponse task1Response = objectMapper.readValue(task1Result.getResponse().getContentAsString(), TaskResponse.class);
        Long task1Id = task1Response.getId();

        // Task 2
        TaskRequest task2Request = new TaskRequest();
        task2Request.setTitle("Flow Task 2");
        task2Request.setDescription("Segunda task del flujo");
        task2Request.setStatus(Status.TODO);
        task2Request.setProjectId(projectId);
        task2Request.setAssigneeId(userId);

        MvcResult task2Result = mockMvc.perform(post("/tasks/newtask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task2Request)))
                .andExpect(status().isCreated())
                .andReturn();
        TaskResponse task2Response = objectMapper.readValue(task2Result.getResponse().getContentAsString(), TaskResponse.class);
        Long task2Id = task2Response.getId();

        // Task 3
        TaskRequest task3Request = new TaskRequest();
        task3Request.setTitle("Flow Task 3");
        task3Request.setDescription("Tercera task del flujo");
        task3Request.setStatus(Status.TODO);
        task3Request.setProjectId(projectId);
        task3Request.setAssigneeId(userId);

        MvcResult task3Result = mockMvc.perform(post("/tasks/newtask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task3Request)))
                .andExpect(status().isCreated())
                .andReturn();
        TaskResponse task3Response = objectMapper.readValue(task3Result.getResponse().getContentAsString(), TaskResponse.class);
        Long task3Id = task3Response.getId();

        // === 4) Transicionar estados de algunas tasks ===
        // Task1: TODO -> IN_PROGRESS -> DONE
        mockMvc.perform(patch("/tasks/" + task1Id + "/status")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/tasks/" + task1Id + "/status")
                        .param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        // Task2: TODO -> IN_PROGRESS
        mockMvc.perform(patch("/tasks/" + task2Id + "/status")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Task3 queda en TODO (sin cambios de estado)

        // === 5) Listar tasks del proyecto para verificar ===
        MvcResult listResult = mockMvc.perform(get("/tasks/project/" + projectId))
                .andExpect(status().isOk())
                .andReturn();

        List<TaskResponse> tasks = objectMapper.readValue(listResult.getResponse().getContentAsString(), new TypeReference<List<TaskResponse>>() {});
        assertEquals(3, tasks.size());

        // Verificar estados
        assertTrue(tasks.stream().anyMatch(t -> t.getId().equals(task1Id) && t.getStatus() == Status.DONE));
        assertTrue(tasks.stream().anyMatch(t -> t.getId().equals(task2Id) && t.getStatus() == Status.IN_PROGRESS));
        assertTrue(tasks.stream().anyMatch(t -> t.getId().equals(task3Id) && t.getStatus() == Status.TODO));

        // === EXTRAS: Probar fallos esperados ===
        // Crear task en proyecto inexistente
        TaskRequest invalidTaskRequest = new TaskRequest();
        invalidTaskRequest.setTitle("Invalid Task");
        invalidTaskRequest.setProjectId(999L);
        invalidTaskRequest.setStatus(Status.TODO);
        mockMvc.perform(post("/tasks/newtask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidTaskRequest)))
                .andExpect(status().isNotFound());

        // Asignar task a usuario inexistente
        TaskRequest invalidUserTaskRequest = new TaskRequest();
        invalidUserTaskRequest.setTitle("Invalid User Task");
        invalidUserTaskRequest.setProjectId(projectId);
        invalidUserTaskRequest.setAssigneeId(999L);
        invalidUserTaskRequest.setStatus(Status.TODO);
        mockMvc.perform(post("/tasks/newtask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUserTaskRequest)))
                .andExpect(status().isNotFound());
    }
}
