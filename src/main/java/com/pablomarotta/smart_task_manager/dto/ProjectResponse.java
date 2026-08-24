package com.pablomarotta.smart_task_manager.dto;

import com.pablomarotta.smart_task_manager.model.ProjectRole;
import lombok.Data;

@Data
public class ProjectResponse {

    private Long id;
    private String name;
    private String objective;
    private long taskCount;

    // Owner info
    private Long ownerId;
    private String ownerUsername;
    private ProjectRole currentUserRole;

    // Timestamps
    private String createdAt;
}
