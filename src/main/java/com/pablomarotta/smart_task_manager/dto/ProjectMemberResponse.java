package com.pablomarotta.smart_task_manager.dto;

import lombok.Data;

@Data
public class ProjectMemberResponse {
    private Long membershipId;
    private Long userId;
    private String username;
    private String fullName;
    private boolean owner;
    private String joinedAt;
}
