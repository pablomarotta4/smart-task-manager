package com.pablomarotta.smart_task_manager.dto;

import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TaskRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @NotNull
    private Status status;

    @NotNull
    private Long projectId;

    private Long assigneeId;

    private Priority priority;

    private String category;

    private LocalDate dueDate;

    private Integer position;
}
