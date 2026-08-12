package com.pablomarotta.smart_task_manager;

import com.pablomarotta.smart_task_manager.integration.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

@SpringBootTest
class SmartTaskManagerApplicationTests extends PostgresIntegrationTest {

    @Autowired
    SmartTaskManagerApplicationTests(DataSource dataSource) {
        super(dataSource);
    }

	@Test
	void contextLoads() {
	}

}
