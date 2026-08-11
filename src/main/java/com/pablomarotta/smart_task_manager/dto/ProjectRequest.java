package com.pablomarotta.smart_task_manager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectRequest {

    @NotBlank
    private String name;

    // Retained temporarily for backward wire compatibility; ownership comes from authentication.
    private String username;
}
