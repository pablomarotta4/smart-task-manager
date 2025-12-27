package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.ProjectRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectResponse;
import com.pablomarotta.smart_task_manager.exception.ProjectNotFoundException;
import com.pablomarotta.smart_task_manager.model.Project;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;

    @Transactional
    public ProjectResponse createProject(ProjectRequest projectRequest){
        try{
            Project project = Project.builder()
                    .name(projectRequest.getName())
                    .owner(projectRequest.getOwner())
                    .build();
            Project savedProject = projectRepository.save(project);
            return mapToResponse(savedProject);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error creating project: " + e.getMessage());
        }
    }

    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    public ProjectResponse getProjectById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + id));
        return mapToResponse(project);
    }

    private ProjectResponse mapToResponse(Project project){
        ProjectResponse projectResponse = new ProjectResponse();
        projectResponse.setId(project.getId());
        projectResponse.setName(project.getName());
        projectResponse.setOwnerId(project.getOwner().getId());
        projectResponse.setOwnerUsername(project.getOwner().getUsername());
        projectResponse.setCreatedAt(project.getCreatedAt() != null ? 
            project.getCreatedAt().toString() : null);

        return projectResponse;
    }
}



