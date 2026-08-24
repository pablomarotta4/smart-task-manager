package com.pablomarotta.smart_task_manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectMemberRequest {

    @NotBlank
    @Size(max = 50)
    private String username;
}
