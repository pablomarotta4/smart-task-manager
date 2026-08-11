package com.pablomarotta.smart_task_manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.PlanningTestFixtures;
import com.pablomarotta.smart_task_manager.client.AIPlanningClient;
import com.pablomarotta.smart_task_manager.client.AIPlanningUnavailableException;
import com.pablomarotta.smart_task_manager.dto.planning.AIPlanningResponse;
import com.pablomarotta.smart_task_manager.dto.planning.PlanQualityMetrics;
import com.pablomarotta.smart_task_manager.dto.planning.PlanQualityReport;
import com.pablomarotta.smart_task_manager.dto.planning.PlanningTicketDraft;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectGenerationDraftResponse;
import com.pablomarotta.smart_task_manager.dto.planning.ProjectPlanDraft;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationRun;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationMode;
import com.pablomarotta.smart_task_manager.model.ProjectGenerationStatus;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.ProjectGenerationRunRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationServiceTest {

    @Mock
    private ProjectGenerationRunRepository runRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AIPlanningClient aiPlanningClient;

    private ProjectGenerationService service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new ProjectGenerationService(
                runRepository,
                userRepository,
                aiPlanningClient,
                new ObjectMapper().findAndRegisterModules()
        );
        owner = User.builder().id(7L).username("alice").email("alice@example.com")
                .password("encoded").fullName("Alice").build();
        when(runRepository.save(any(ProjectGenerationRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void generateDraftUsesAuthenticatedOwnerAndPersistsDraftWithoutApplyingIt() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(aiPlanningClient.generatePlan(any(UUID.class), any(String.class)))
                .thenAnswer(invocation -> PlanningTestFixtures.response(invocation.getArgument(0)));

        ProjectGenerationDraftResponse result = service.generateDraft(
                "alice",
                "Build a useful household budget application"
        );

        assertNotNull(result.runId());
        assertEquals(ProjectGenerationStatus.DRAFT_READY, result.status());
        assertEquals("Budget App", result.draft().name());
        assertEquals(100, result.quality().score());

        ArgumentCaptor<ProjectGenerationRun> runCaptor = ArgumentCaptor.forClass(ProjectGenerationRun.class);
        verify(runRepository, org.mockito.Mockito.times(2)).save(runCaptor.capture());
        ProjectGenerationRun stored = runCaptor.getValue();
        assertEquals(owner, stored.getRequestedBy());
        assertEquals(ProjectGenerationStatus.DRAFT_READY, stored.getStatus());
        assertEquals(ProjectGenerationMode.NEW_PROJECT, stored.getMode());
        assertNotNull(stored.getDraftJson());
        assertEquals(null, stored.getProject());
        verify(aiPlanningClient).generatePlan(stored.getId(), stored.getPrompt());
    }

    @Test
    void generateDraftPersistsFailedStateWhenProviderFails() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(aiPlanningClient.generatePlan(any(UUID.class), any(String.class)))
                .thenThrow(new AIPlanningUnavailableException("offline"));

        assertThrows(
                AIPlanningUnavailableException.class,
                () -> service.generateDraft("alice", "Build a useful household budget application")
        );

        ArgumentCaptor<ProjectGenerationRun> runCaptor = ArgumentCaptor.forClass(ProjectGenerationRun.class);
        verify(runRepository, org.mockito.Mockito.times(2)).save(runCaptor.capture());
        assertEquals(ProjectGenerationStatus.FAILED, runCaptor.getValue().getStatus());
        assertEquals("AI_PLANNING_UNAVAILABLE", runCaptor.getValue().getErrorCode());
    }

}
