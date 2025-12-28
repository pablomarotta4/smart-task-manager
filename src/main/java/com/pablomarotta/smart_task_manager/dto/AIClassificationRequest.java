package com.pablomarotta.smart_task_manager.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIClassificationRequest {
    @NotBlank(message = "El título es obligatorio")
    private String title;
    
    private String description;
}
