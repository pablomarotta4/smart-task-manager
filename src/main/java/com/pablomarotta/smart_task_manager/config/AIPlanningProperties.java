package com.pablomarotta.smart_task_manager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.planning")
public class AIPlanningProperties {
    private String baseUrl = "http://127.0.0.1:8000";
    private int connectTimeoutMs = 2_000;
    private int readTimeoutMs = 180_000;
}
