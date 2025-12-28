package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.config.AIConfigProperties;
import com.pablomarotta.smart_task_manager.dto.AIClassificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OllamaServiceTest {

    private OllamaService ollamaService;

    @BeforeEach
    void setUp() {
        AIConfigProperties properties = Mockito.mock(AIConfigProperties.class);
        ollamaService = new OllamaService(properties);
    }

    @Test
    void testExtractJsonFromText() throws Exception {
        Method method = OllamaService.class.getDeclaredMethod("extractJsonFromText", String.class);
        method.setAccessible(true);

        String textWithJson = "Aquí está el resultado: {\"priority\": \"HIGH\", \"category\": \"BUG\", \"estimatedDays\": 2, \"summary\": \"Test\"} espero que sirva.";
        Optional<String> result = (Optional<String>) method.invoke(ollamaService, textWithJson);
        assertTrue(result.isPresent());
        assertEquals("{\"priority\": \"HIGH\", \"category\": \"BUG\", \"estimatedDays\": 2, \"summary\": \"Test\"}", result.get());

        String textWithoutJson = "No hay nada aquí";
        Optional<String> resultEmpty = (Optional<String>) method.invoke(ollamaService, textWithoutJson);
        assertTrue(resultEmpty.isEmpty());
    }

    @Test
    void testParseResponse() throws Exception {
        Method method = OllamaService.class.getDeclaredMethod("parseResponse", String.class);
        method.setAccessible(true);

        String validResponse = "```json\n{\"priority\": \"HIGH\", \"category\": \"BUG\", \"estimatedDays\": 2, \"summary\": \"Fix bug\"}\n```";
        AIClassificationResponse response = (AIClassificationResponse) method.invoke(ollamaService, validResponse);

        assertNotNull(response);
        assertEquals("HIGH", response.getPriority());
        assertEquals("BUG", response.getCategory());
        assertEquals(2, response.getEstimatedDays());
        assertEquals("Fix bug", response.getSummary());
    }
}
