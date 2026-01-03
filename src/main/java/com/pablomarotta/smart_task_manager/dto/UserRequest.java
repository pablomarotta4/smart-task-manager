package com.pablomarotta.smart_task_manager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Email is required")
    @jakarta.validation.constraints.Email(message = "Email must be valid")
    private String email;

    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;
}
