package com.pablomarotta.smart_task_manager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OllamaRequest {
    private String model;
    private String prompt;
    private Integer maxTokens;
    private Double temperature;
    private boolean stream = false;
}
