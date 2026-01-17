package com.pablomarotta.smart_task_manager.service;

import org.springframework.stereotype.Service;

import com.pablomarotta.smart_task_manager.dto.AIClassificationRequest;
import com.pablomarotta.smart_task_manager.dto.AIClassificationResponse;
import com.pablomarotta.smart_task_manager.config.AIConfigProperties;

@Service
public class AIService {
    private final OllamaService ollamaService;
    private final AIConfigProperties aiConfigProperties;

    public AIService(OllamaService ollamaService, AIConfigProperties aiConfigProperties) {
        this.ollamaService = ollamaService;
        this.aiConfigProperties = aiConfigProperties;
    }

    public AIClassificationResponse classifyTask(AIClassificationRequest request) {
        if (!aiConfigProperties.isEnabled()) {
            return new AIClassificationResponse();
        }

        try {
            return ollamaService.classifyTask(request);
        } catch (RuntimeException ex) {
            return new AIClassificationResponse();
        }
    }
}
