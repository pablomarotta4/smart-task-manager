package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.ProjectRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectResponse;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.exception.UserNotFoundException;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectService projectService;

    private Project project;
    private ProjectRequest projectRequest;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("testuser");

        project = Project.builder()
                .id(1L)
                .name("Test Project")
                .owner(owner)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        projectRequest = new ProjectRequest();
        projectRequest.setName("Test Project");
        projectRequest.setUsername("testuser");
    }

    @Test
    void createProject_ShouldReturnCreatedProject() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(owner));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        // Act
        ProjectResponse response = projectService.createProject(projectRequest);

        // Assert
        assertNotNull(response);
        assertEquals(project.getId(), response.getId());
        assertEquals(project.getName(), response.getName());
        assertEquals(owner.getId(), response.getOwnerId());
        assertEquals(owner.getUsername(), response.getOwnerUsername());
        verify(userRepository, times(1)).findByUsername("testuser");
        verify(projectRepository, times(1)).save(any(Project.class));
    }

    @Test
    void createProject_WhenUserNotFound_ShouldThrowUserNotFoundException() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UserNotFoundException.class, () -> {
            projectService.createProject(projectRequest);
        });
        verify(userRepository, times(1)).findByUsername("testuser");
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void createProject_WhenExceptionThrown_ShouldThrowResponseStatusException() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(owner));
        when(projectRepository.save(any(Project.class))).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> {
            projectService.createProject(projectRequest);
        });
    }

    @Test
    void getAllProjects_ShouldReturnAllProjects() {
        // Arrange
        Project project2 = Project.builder()
                .id(2L)
                .name("Another Project")
                .owner(owner)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        when(projectRepository.findAll()).thenReturn(Arrays.asList(project, project2));

        // Act
        List<ProjectResponse> projects = projectService.getAllProjects();

        // Assert
        assertNotNull(projects);
        assertEquals(2, projects.size());
        assertEquals("Test Project", projects.get(0).getName());
        assertEquals("Another Project", projects.get(1).getName());
        verify(projectRepository, times(1)).findAll();
    }

    @Test
    void getProjectById_WithValidId_ShouldReturnProject() {
        // Arrange
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        // Act
        ProjectResponse response = projectService.getProjectById(1L);

        // Assert
        assertNotNull(response);
        assertEquals(project.getId(), response.getId());
        assertEquals(project.getName(), response.getName());
        verify(projectRepository, times(1)).findById(1L);
    }

    @Test
    void getProjectById_WithInvalidId_ShouldThrowProjectNotFoundException() {
        // Arrange
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ProjectNotFoundException.class, () -> {
            projectService.getProjectById(99L);
        });
        verify(projectRepository, times(1)).findById(99L);
    }
}
