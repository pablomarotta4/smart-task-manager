package com.pablomarotta.smart_task_manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.TaskRequest;
import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private TaskService taskService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UsernamePasswordAuthenticationToken principal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(taskService)).build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        principal = new UsernamePasswordAuthenticationToken("alice", "ignored");
    }

    @Test
    void createAndUpdateUseAuthenticatedUsername() throws Exception {
        TaskRequest request = validRequest();

        mockMvc.perform(post("/api/tasks/newtask")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/tasks/101")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(taskService).createTask(any(TaskRequest.class), eq("alice"));
        verify(taskService).updateTask(eq(101L), any(TaskRequest.class), eq("alice"));
    }

    @Test
    void readEndpointsUseAuthenticatedUsername() throws Exception {
        mockMvc.perform(get("/api/tasks/alltasks").principal(principal)).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks/project/20").principal(principal)).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks/user/2").principal(principal)).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks/status/todo").principal(principal)).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks/project/20/users").principal(principal)).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks/101").principal(principal)).andExpect(status().isOk());

        verify(taskService).getAllTasks("alice");
        verify(taskService).getTasksByProjectId(20L, "alice");
        verify(taskService).getTasksByUserId(2L, "alice");
        verify(taskService).getTodoTasks("alice");
        verify(taskService).getAllUsersInProject(20L, "alice");
        verify(taskService).getTaskById(101L, "alice");
    }

    @Test
    void mutationEndpointsUseAuthenticatedUsername() throws Exception {
        mockMvc.perform(delete("/api/tasks/101").principal(principal))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/tasks/101/status")
                        .principal(principal)
                        .param("status", "DONE"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/tasks/101/assign")
                        .principal(principal)
                        .param("userId", "2"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/tasks/101/priority")
                        .principal(principal)
                        .param("priority", "URGENT"))
                .andExpect(status().isOk());

        verify(taskService).deleteTask(101L, "alice");
        verify(taskService).updateTaskStatus(101L, Status.DONE, "alice");
        verify(taskService).assignTask(101L, 2L, "alice");
        verify(taskService).updateTaskPriority(101L, Priority.URGENT, "alice");
    }

    private TaskRequest validRequest() {
        TaskRequest request = new TaskRequest();
        request.setTitle("Create opportunity intake");
        request.setDescription("Capture the company, role, source, and application link.");
        request.setStatus(Status.TODO);
        request.setProjectId(20L);
        request.setPriority(Priority.HIGH);
        request.setPosition(0);
        return request;
    }
}
