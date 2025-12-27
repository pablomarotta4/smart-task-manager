package com.pablomarotta.smart_task_manager.dto;

import com.pablomarotta.smart_task_manager.model.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectRequest {

    @NotBlank
    private String name;

    @NotNull
    private User owner;
}
