package com.pablomarotta.smart_task_manager.dto;

import lombok.Data;

@Data
public class ProjectResponse {

    private Long id;
    private String name;

    // Owner info
    private Long ownerId;
    private String ownerUsername;

    // Timestamps
    private String createdAt;
}
