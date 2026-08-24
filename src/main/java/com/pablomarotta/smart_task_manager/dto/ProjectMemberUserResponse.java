package com.pablomarotta.smart_task_manager.dto;

public record ProjectMemberUserResponse(
        Long id,
        String username,
        String fullName
) {
}
