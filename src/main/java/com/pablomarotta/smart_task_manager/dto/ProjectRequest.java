package com.pablomarotta.smart_task_manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectRequest {

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 4000)
    private String objective;

    // Retained temporarily for backward wire compatibility; ownership comes from authentication.
    private String username;
}
