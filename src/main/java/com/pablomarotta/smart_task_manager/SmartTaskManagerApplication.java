package com.pablomarotta.smart_task_manager;

import com.pablomarotta.smart_task_manager.config.EmailOutboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(EmailOutboxProperties.class)
public class SmartTaskManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartTaskManagerApplication.class, args);
	}

}
