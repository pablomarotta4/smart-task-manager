package com.pablomarotta.smart_task_manager.dto;

import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TaskResponse {

    private Long id;
    private String title;
    private String description;
    private Status status;
    private Integer position;
    private Priority priority;
    private String category;
    private LocalDate dueDate;

    // Project info
    private Long projectId;
    private String projectName;

    // User info
    private Long assigneeId;
    private String assigneeUsername;
    private Long createdById;
    private String createdByUsername;

    // Timestamps
    private String createdAt;
    private String updatedAt;
    private String completedAt;

    // AI classification fields
    private Priority aiPriority;
    private String aiCategory;
    private Integer aiSuggestedDueDays;
    private LocalDate aiSuggestedDueDate;
    private String aiSummary;
}
