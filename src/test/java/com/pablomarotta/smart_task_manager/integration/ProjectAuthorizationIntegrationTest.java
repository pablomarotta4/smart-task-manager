package com.pablomarotta.smart_task_manager.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.AuthResponse;
import com.pablomarotta.smart_task_manager.dto.ProjectMemberRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectRequest;
import com.pablomarotta.smart_task_manager.dto.ProjectResponse;
import com.pablomarotta.smart_task_manager.dto.RegisterRequest;
import com.pablomarotta.smart_task_manager.dto.TaskRequest;
import com.pablomarotta.smart_task_manager.dto.TaskResponse;
import com.pablomarotta.smart_task_manager.model.Project;
import com.pablomarotta.smart_task_manager.model.ProjectMembership;
import com.pablomarotta.smart_task_manager.model.ProjectRole;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.Task;
import com.pablomarotta.smart_task_manager.model.TaskAcceptanceCriterion;
import com.pablomarotta.smart_task_manager.model.TaskDependency;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectMembershipRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskAcceptanceCriterionRepository;
import com.pablomarotta.smart_task_manager.repository.TaskDependencyRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectAuthorizationIntegrationTest extends PostgresIntegrationTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TaskAcceptanceCriterionRepository criterionRepository;
    private final TaskDependencyRepository dependencyRepository;

    @Autowired
    ProjectAuthorizationIntegrationTest(
            DataSource dataSource,
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            ProjectRepository projectRepository,
            ProjectMembershipRepository membershipRepository,
            UserRepository userRepository,
            TaskRepository taskRepository,
            TaskAcceptanceCriterionRepository criterionRepository,
            TaskDependencyRepository dependencyRepository
    ) {
        super(dataSource);
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.criterionRepository = criterionRepository;
        this.dependencyRepository = dependencyRepository;
    }

    @Test
    void enforcesMembershipCapabilitiesWithoutGlobalRoleBypass() throws Exception {
        AuthResponse owner = register("cap-owner");
        AuthResponse manager = register("cap-manager");
        AuthResponse member = register("cap-member");
        AuthResponse managerTarget = register("cap-manager-target");
        AuthResponse outsider = register("cap-outsider");
        ProjectResponse project = createProject(owner);
        Project persistedProject = projectRepository.findById(project.getId()).orElseThrow();

        addMembership(persistedProject, manager.getUser().getUsername(), ProjectRole.MANAGER);
        addMembership(persistedProject, member.getUser().getUsername(), ProjectRole.MEMBER);
        addMembership(persistedProject, managerTarget.getUser().getUsername(), ProjectRole.MANAGER);
        TaskResponse assignedTask = createTask(owner, project.getId(), member.getUser().getId(), "Member task");

        assertEquals(ProjectRole.OWNER, project.getCurrentUserRole());
        mockMvc.perform(get("/api/projects")
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/projects")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentUserRole").value("MEMBER"));
        mockMvc.perform(get("/api/projects/{projectId}", project.getId())
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/{projectId}", project.getId())
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUserRole").value("MEMBER"));
        mockMvc.perform(get("/api/tasks/project/{projectId}/users", project.getId())
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'cap-member')].email").doesNotExist())
                .andExpect(jsonPath("$[?(@.username == 'cap-member')].role").doesNotExist());
        mockMvc.perform(get("/api/tasks/project/{projectId}/users", project.getId())
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/tasks/{taskId}/status", assignedTask.getId())
                        .header("Authorization", bearer(member))
                        .param("status", Status.DONE.name()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/tasks/{taskId}/priority", assignedTask.getId())
                        .header("Authorization", bearer(member))
                        .param("priority", "HIGH"))
                .andExpect(status().isForbidden());

        TaskResponse managerTask = createTask(manager, project.getId(), null, "Manager task");
        assertEquals(manager.getUser().getUsername(), managerTask.getCreatedByUsername());
        mockMvc.perform(delete("/api/projects/{projectId}/members/{userId}", project.getId(), managerTarget.getUser().getId())
                        .header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/projects/{projectId}/members/{userId}", project.getId(), managerTarget.getUser().getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void taskReadRoutesSerializeVisibleAssociationsWithOpenSessionInViewDisabled() throws Exception {
        AuthResponse owner = register("task-read-owner");
        AuthResponse member = register("task-read-member");
        ProjectResponse project = createProject(owner);
        Project persistedProject = projectRepository.findById(project.getId()).orElseThrow();
        addMembership(persistedProject, member.getUser().getUsername(), ProjectRole.MEMBER);

        for (Status statusValue : Status.values()) {
            createTask(
                    owner,
                    project.getId(),
                    member.getUser().getId(),
                    "Task " + statusValue,
                    statusValue
            );
        }

        mockMvc.perform(get("/api/tasks/alltasks").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectName").value(project.getName()))
                .andExpect(jsonPath("$[0].assigneeUsername").value(member.getUser().getUsername()))
                .andExpect(jsonPath("$[0].createdByUsername").value(owner.getUser().getUsername()));
        mockMvc.perform(get("/api/tasks/project/{projectId}", project.getId())
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectName").value(project.getName()));
        mockMvc.perform(get("/api/tasks/user/{userId}", member.getUser().getId())
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assigneeUsername").value(member.getUser().getUsername()));
        for (Status statusValue : Status.values()) {
            mockMvc.perform(get("/api/tasks/status/{status}", statusPath(statusValue))
                            .header("Authorization", bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value(statusValue.name()));
        }
    }

    @Test
    void assignedMemberCannotChangeDueDateOrCategoryThroughWholeTaskPut() throws Exception {
        AuthResponse owner = register("whole-put-owner");
        AuthResponse member = register("whole-put-member");
        ProjectResponse project = createProject(owner);
        Project persistedProject = projectRepository.findById(project.getId()).orElseThrow();
        addMembership(persistedProject, member.getUser().getUsername(), ProjectRole.MEMBER);
        TaskResponse createdTask = createTask(
                owner,
                project.getId(),
                member.getUser().getId(),
                "Protected ticket"
        );
        Task persistedTask = taskRepository.findById(createdTask.getId()).orElseThrow();
        persistedTask.setDueDate(java.time.LocalDate.of(2026, 8, 20));
        persistedTask.setCategory("Original category");
        taskRepository.saveAndFlush(persistedTask);

        TaskRequest update = new TaskRequest();
        update.setProjectId(project.getId());
        update.setTitle(persistedTask.getTitle());
        update.setDescription(persistedTask.getDescription());
        update.setStatus(persistedTask.getStatus());
        update.setAssigneeId(member.getUser().getId());
        update.setPriority(persistedTask.getPriority());
        update.setPosition(persistedTask.getPosition());
        update.setDueDate(java.time.LocalDate.of(2026, 8, 21));
        update.setCategory("Escalated category");

        mockMvc.perform(put("/api/tasks/{taskId}", createdTask.getId())
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());

        Task unchanged = taskRepository.findById(createdTask.getId()).orElseThrow();
        assertEquals(java.time.LocalDate.of(2026, 8, 20), unchanged.getDueDate());
        assertEquals("Original category", unchanged.getCategory());
    }

    @Test
    void excludesLegacyAssignmentWithoutCurrentMembershipFromMyWorkQueries() throws Exception {
        AuthResponse owner = register("legacy-work-owner");
        AuthResponse formerMember = register("legacy-work-member");
        ProjectResponse project = createProject(owner);
        Project persistedProject = projectRepository.findById(project.getId()).orElseThrow();
        addMembership(persistedProject, formerMember.getUser().getUsername(), ProjectRole.MEMBER);

        TaskResponse prerequisite = createTask(owner, project.getId(), null, "Legacy prerequisite");
        TaskResponse legacyAssignment = createTask(
                owner,
                project.getId(),
                formerMember.getUser().getId(),
                "Legacy assignment"
        );
        Task persistedPrerequisite = taskRepository.findById(prerequisite.getId()).orElseThrow();
        Task persistedLegacyAssignment = taskRepository.findById(legacyAssignment.getId()).orElseThrow();
        criterionRepository.saveAndFlush(TaskAcceptanceCriterion.builder()
                .task(persistedLegacyAssignment)
                .criterion("Must stay private")
                .position(0)
                .build());
        dependencyRepository.saveAndFlush(TaskDependency.builder()
                .task(persistedLegacyAssignment)
                .dependsOnTask(persistedPrerequisite)
                .build());

        ProjectMembership membership = membershipRepository.findByProjectIdAndUserId(
                project.getId(),
                formerMember.getUser().getId()
        ).orElseThrow();
        membershipRepository.delete(membership);
        membershipRepository.flush();

        assertAll(
                () -> assertEquals(
                        List.of(),
                        taskRepository.findByAssigneeUsernameOrderByDueDateAscPositionAsc(
                                formerMember.getUser().getUsername()
                        )
                ),
                () -> assertEquals(
                        List.of(),
                        criterionRepository.findByTaskAssigneeUsername(formerMember.getUser().getUsername())
                ),
                () -> assertEquals(
                        List.of(),
                        dependencyRepository.findByTaskAssigneeUsername(formerMember.getUser().getUsername())
                ),
                () -> mockMvc.perform(get("/api/tasks/my-work")
                                .header("Authorization", bearer(formerMember)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$").isEmpty())
        );
    }

    private AuthResponse register(String username) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("password123");
        request.setFullName(username);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private ProjectResponse createProject(AuthResponse owner) throws Exception {
        ProjectRequest request = new ProjectRequest();
        request.setName("Capability project " + owner.getUser().getUsername());

        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentUserRole").value("OWNER"))
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ProjectResponse.class);
    }

    private TaskResponse createTask(
            AuthResponse actor,
            Long projectId,
            Long assigneeId,
            String title
    ) throws Exception {
        return createTask(actor, projectId, assigneeId, title, Status.TODO);
    }

    private TaskResponse createTask(
            AuthResponse actor,
            Long projectId,
            Long assigneeId,
            String title,
            Status status
    ) throws Exception {
        TaskRequest request = new TaskRequest();
        request.setProjectId(projectId);
        request.setTitle(title);
        request.setStatus(status);
        request.setAssigneeId(assigneeId);

        MvcResult result = mockMvc.perform(post("/api/tasks/newtask")
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TaskResponse.class);
    }

    private void addMembership(Project project, String username, ProjectRole role) {
        User user = userRepository.findByUsername(username).orElseThrow();
        membershipRepository.saveAndFlush(ProjectMembership.builder()
                .project(project)
                .user(user)
                .role(role)
                .build());
    }

    private String bearer(AuthResponse response) {
        return "Bearer " + response.getToken();
    }

    private String statusPath(Status status) {
        return switch (status) {
            case TODO -> "todo";
            case IN_PROGRESS -> "in-progress";
            case DONE -> "done";
            case BLOCKED -> "blocked";
            case CANCELLED -> "cancelled";
        };
    }
}
