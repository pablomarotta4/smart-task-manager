package com.pablomarotta.smart_task_manager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String type;
    private UserResponse user;
}
