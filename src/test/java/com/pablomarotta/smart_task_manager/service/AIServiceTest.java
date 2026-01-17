package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.config.AIConfigProperties;
import com.pablomarotta.smart_task_manager.dto.AIClassificationRequest;
import com.pablomarotta.smart_task_manager.dto.AIClassificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
class AIServiceTest {

    private AIConfigProperties aiConfigProperties;
    private AIService aiService;
    private StubOllamaService stubOllamaService;

    @BeforeEach
    void setUp() {
        aiConfigProperties = new AIConfigProperties();
        stubOllamaService = new StubOllamaService(aiConfigProperties);
        aiService = new AIService(stubOllamaService, aiConfigProperties);
    }

    @Test
    void classifyTask_WhenDisabled_ReturnsDefaultsWithoutCallingOllama() {
        aiConfigProperties.setEnabled(false);

        AIClassificationRequest request = AIClassificationRequest.builder()
                .title("title")
                .description("description")
                .build();

        AIClassificationResponse response = aiService.classifyTask(request);

        assertNotNull(response);
        assertNull(response.getPriority());
        assertNull(response.getCategory());
        assertNull(response.getSummary());
        assertEquals(0, response.getEstimatedDays());
        assertEquals(0, stubOllamaService.getCallCount());
    }

    @Test
    void classifyTask_WhenOllamaFails_ReturnsDefaults() {
        aiConfigProperties.setEnabled(true);
        AIClassificationRequest request = AIClassificationRequest.builder()
                .title("title")
                .description("description")
                .build();

        stubOllamaService.setException(new RuntimeException("boom"));

        AIClassificationResponse response = aiService.classifyTask(request);

        assertNotNull(response);
        assertNull(response.getPriority());
        assertNull(response.getCategory());
        assertNull(response.getSummary());
        assertEquals(0, response.getEstimatedDays());
    }

    private static class StubOllamaService extends OllamaService {
        private RuntimeException exception;
        private int callCount;

        StubOllamaService(AIConfigProperties aiConfigProperties) {
            super(aiConfigProperties);
        }

        @Override
        public AIClassificationResponse classifyTask(AIClassificationRequest request) {
            callCount++;
            if (exception != null) {
                throw exception;
            }
            return new AIClassificationResponse();
        }

        void setException(RuntimeException exception) {
            this.exception = exception;
        }

        int getCallCount() {
            return callCount;
        }
    }
}
