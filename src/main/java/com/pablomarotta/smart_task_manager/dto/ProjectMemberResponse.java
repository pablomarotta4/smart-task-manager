package com.pablomarotta.smart_task_manager.dto;

import com.pablomarotta.smart_task_manager.model.ProjectRole;
import lombok.Data;

@Data
public class ProjectMemberResponse {
    private Long membershipId;
    private Long userId;
    private String username;
    private String fullName;
    private boolean owner;
    private ProjectRole role;
    private String joinedAt;
}
