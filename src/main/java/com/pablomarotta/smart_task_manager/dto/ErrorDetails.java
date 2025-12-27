package com.pablomarotta.smart_task_manager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorDetails {
    LocalDateTime timestamp;
    String message;
    String details;
    int status;
}
