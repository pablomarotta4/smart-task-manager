package com.pablomarotta.smart_task_manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.config.AIConfigProperties;
import com.pablomarotta.smart_task_manager.dto.AIClassificationRequest;
import com.pablomarotta.smart_task_manager.dto.AIClassificationResponse;

import com.pablomarotta.smart_task_manager.dto.OllamaRequest;
import com.pablomarotta.smart_task_manager.dto.OllamaResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

@Service
public class OllamaService {
    private final AIConfigProperties aiConfigProperties;
    private final WebClient webClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaService(AIConfigProperties aiConfigProperties) {
        this.aiConfigProperties = aiConfigProperties;
        this.webClient = WebClient.builder().build();
    }

    private String buildPrompt(String title, String description) {
        return """
            Eres un asistente de gestión de tareas. Analiza la siguiente tarea y devuelve un JSON con:
            - priority: "LOW", "MEDIUM", "HIGH" o "URGENT"
            - category: una categoría adecuada (ej: "BUG", "FEATURE", "DOCUMENTATION", "REFACTOR")
            - estimatedDays: número estimado de días para completar (entero)
            - summary: un resumen conciso de la tarea (máximo 100 palabras)

            Tarea: %s
            %s

            Devuelve SOLO el JSON, sin texto adicional.
            """.formatted(
                title,
                description != null && !description.isBlank() ? "Descripción: " + description : ""
        );
    }

    public AIClassificationResponse classifyTask(AIClassificationRequest request) {
        try{
            String prompt = buildPrompt(request.getTitle(), request.getDescription());

            OllamaRequest ollamaRequest = OllamaRequest.builder().model(aiConfigProperties.getModel())
                    .prompt(prompt)
                    .maxTokens(aiConfigProperties.getMaxTokens())
                    .temperature(aiConfigProperties.getTemperature())
                    .build();

            OllamaResponse ollamaResponse = webClient.post()
                    .uri(aiConfigProperties.getBaseUrl() + "/api/generate")
                    .bodyValue(ollamaRequest)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .block();

            return parseResponse(ollamaResponse.getResponse());
        } catch (Exception e) {
            throw new RuntimeException("Error clasifying task", e);
        }
    }

    private AIClassificationResponse parseResponse(String response) {
        try{
            String jsonString = extractJsonFromText(response)
                    .orElseThrow(() -> new RuntimeException("No JSON found in response"));

            return objectMapper.readValue(jsonString, AIClassificationResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing response", e);
        }
    }

    private Optional<String> extractJsonFromText(String text) {
        if (text == null) return Optional.empty();
        
        int startIndex = text.indexOf('{');
        int endIndex = text.lastIndexOf('}');

        if (startIndex >= 0 && endIndex >= startIndex) {
            return Optional.of(text.substring(startIndex, endIndex + 1));
        }

        return Optional.empty();
    }
}
