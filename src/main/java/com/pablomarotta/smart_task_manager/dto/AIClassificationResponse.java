package com.pablomarotta.smart_task_manager.dto;

import lombok.Data;

@Data
public class AIClassificationResponse {
    private String priority;
    private String category;
    private int estimatedDays;
    private String summary;
}
