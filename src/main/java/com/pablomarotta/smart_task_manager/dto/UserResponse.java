package com.pablomarotta.smart_task_manager.dto;

import lombok.Data;

@Data
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private Boolean active;
    private String createdAt;
    private String updatedAt;
}
