package com.pablomarotta.smart_task_manager.dto;

import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

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
    private BigDecimal estimatedHours;
    private String planningClientId;
    private List<String> acceptanceCriteria = List.of();
    private List<String> dependsOn = List.of();

    // Project info
    private Long projectId;
    private String projectName;
    private Long parentTaskId;

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
