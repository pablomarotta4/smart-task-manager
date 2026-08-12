package com.pablomarotta.smart_task_manager.integration;

import com.pablomarotta.smart_task_manager.PlanningTestFixtures;
import com.pablomarotta.smart_task_manager.client.AIPlanningClient;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationConfirmationResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationDraftResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectPlanDraft;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectGenerationRunRepository;
import com.pablomarotta.smart_task_manager.repository.ProjectRepository;
import com.pablomarotta.smart_task_manager.repository.TaskAcceptanceCriterionRepository;
import com.pablomarotta.smart_task_manager.repository.TaskDependencyRepository;
import com.pablomarotta.smart_task_manager.repository.TaskRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.service.ProjectGenerationConfirmationService;
import com.pablomarotta.smart_task_manager.service.ProjectGenerationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "ai.ollama.enabled=false")
class ProjectGenerationIntegrationTest extends PostgresIntegrationTest {

    private final ProjectGenerationService generationService;
    private final ProjectGenerationConfirmationService confirmationService;
    private final ProjectGenerationRunRepository runRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskAcceptanceCriterionRepository criterionRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final UserRepository userRepository;

    @MockBean
    private AIPlanningClient aiPlanningClient;

    @Autowired
    ProjectGenerationIntegrationTest(
            DataSource dataSource,
            ProjectGenerationService generationService,
            ProjectGenerationConfirmationService confirmationService,
            ProjectGenerationRunRepository runRepository,
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            TaskAcceptanceCriterionRepository criterionRepository,
            TaskDependencyRepository dependencyRepository,
            UserRepository userRepository
    ) {
        super(dataSource);
        this.generationService = generationService;
        this.confirmationService = confirmationService;
        this.runRepository = runRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.criterionRepository = criterionRepository;
        this.dependencyRepository = dependencyRepository;
        this.userRepository = userRepository;
    }

    @BeforeEach
    void setUp() {
        cleanDatabase();
        userRepository.save(User.builder()
                .username("integration-planner")
                .email("integration-planner@example.com")
                .password("encoded-password")
                .fullName("Integration Planner")
                .build());
        when(aiPlanningClient.generatePlan(any(UUID.class), any(String.class)))
                .thenAnswer(invocation -> PlanningTestFixtures.response(invocation.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void generatesEditableDraftThenAtomicallyCreatesProjectAndFirstTickets() {
        ProjectGenerationDraftResponse generated = generationService.generateDraft(
                "integration-planner",
                "Build a useful household budget application"
        );

        assertEquals(0, projectRepository.count());
        assertEquals(0, taskRepository.count());
        assertTrue(generated.quality().passed());

        ProjectPlanDraft draft = generated.draft();
        ProjectPlanDraft edited = new ProjectPlanDraft(
                "Family Budget MVP",
                draft.objective(),
                draft.assumptions(),
                draft.risks(),
                draft.openQuestions(),
                draft.tickets()
        );
        ProjectGenerationConfirmationResponse confirmed = confirmationService.confirm(
                generated.runId(),
                "integration-planner",
                edited
        );

        assertFalse(confirmed.alreadyConfirmed());
        assertEquals("Family Budget MVP", confirmed.projectName());
        assertEquals(3, confirmed.taskIds().size());
        assertEquals(1, projectRepository.count());
        assertEquals(3, taskRepository.count());
        assertEquals(6, criterionRepository.count());
        assertEquals(2, dependencyRepository.count());
        assertEquals(
                "Help a household understand and manage its monthly budget.",
                projectRepository.findById(confirmed.projectId()).orElseThrow().getObjective()
        );

        ProjectGenerationConfirmationResponse repeated = confirmationService.confirm(
                generated.runId(),
                "integration-planner",
                edited
        );

        assertTrue(repeated.alreadyConfirmed());
        assertEquals(confirmed.projectId(), repeated.projectId());
        assertEquals(3, taskRepository.count());
    }

    private void cleanDatabase() {
        dependencyRepository.deleteAll();
        criterionRepository.deleteAll();
        taskRepository.deleteAll();
        runRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }
}
