package com.pablomarotta.smart_task_manager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String username;
}
