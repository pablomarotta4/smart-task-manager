package com.pablomarotta.smart_task_manager.controller;

import com.pablomarotta.smart_task_manager.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.pablomarotta.smart_task_manager.dto.AIClassificationRequest;
import com.pablomarotta.smart_task_manager.dto.AIClassificationResponse;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AIController {
    private final AIService aiService;

    @PostMapping("/classify-task")
    public AIClassificationResponse classifyTask(@RequestBody AIClassificationRequest request) {
        return aiService.classifyTask(request);
    }
}
