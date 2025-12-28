package com.pablomarotta.smart_task_manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.ProjectRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectResponse;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private ProjectController projectController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ProjectResponse projectResponse;
    private ProjectRequest projectRequest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(projectController).build();
        objectMapper = new ObjectMapper();

        projectRequest = new ProjectRequest();
        projectRequest.setName("Test Project");
        projectRequest.setUsername("testuser");
        
        projectResponse = new ProjectResponse();
        projectResponse.setId(1L);
        projectResponse.setName("Test Project");
        projectResponse.setOwnerId(1L);
        projectResponse.setOwnerUsername("testuser");
    }

    @Test
    void createProject_ShouldReturnCreatedProject() throws Exception {
        when(projectService.createProject(any(ProjectRequest.class))).thenReturn(projectResponse);

        mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Project"))
                .andExpect(jsonPath("$.ownerId").value(1))
                .andExpect(jsonPath("$.ownerUsername").value("testuser"));
    }

    @Test
    void createProject_WithInvalidData_ShouldReturnBadRequest() throws Exception {
        ProjectRequest invalidRequest = new ProjectRequest();
        invalidRequest.setName(""); // Invalid: name is blank
        invalidRequest.setUsername(""); // Invalid: username is blank

        mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllProjects_ShouldReturnListOfProjects() throws Exception {
        ProjectResponse project2 = new ProjectResponse();
        project2.setId(2L);
        project2.setName("Another Project");
        project2.setOwnerId(1L);
        project2.setOwnerUsername("testuser");

        when(projectService.getAllProjects()).thenReturn(Arrays.asList(projectResponse, project2));

        mockMvc.perform(get("/projects")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void getProjectById_WithValidId_ShouldReturnProject() throws Exception {
        when(projectService.getProjectById(1L)).thenReturn(projectResponse);

        mockMvc.perform(get("/projects/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Project"));
    }

    @Test
    void getProjectById_WithNonExistentId_ShouldReturnNotFound() throws Exception {
        when(projectService.getProjectById(99L)).thenThrow(new ProjectNotFoundException("Project not found with id: 99"));

        mockMvc.perform(get("/projects/99")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found with id: 99"));
    }
}
