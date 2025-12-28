package com.pablomarotta.smart_task_manager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.ollama")
public class AIConfigProperties {

    private boolean enabled = true;
    private String baseUrl = "http://localhost:11434";
    private String model = "llama3.2";
    private Integer timeout = 30000; // ms
    private Integer maxTokens = 1000;
    private Double temperature = 0.7;
}
