package com.pablomarotta.smart_task_manager.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailVerificationConfirmRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}
