package com.pablomarotta.smart_task_manager.integration;

import com.pablomarotta.smart_task_manager.email.EmailDelivery;
import com.pablomarotta.smart_task_manager.email.SmtpEmailDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EmailOutboxMailConfigurationIntegrationTest extends PostgresIntegrationTest {

    private final EmailDelivery emailDelivery;

    @Autowired
    EmailOutboxMailConfigurationIntegrationTest(DataSource dataSource, EmailDelivery emailDelivery) {
        super(dataSource);
        this.emailDelivery = emailDelivery;
    }

    @Test
    void integrationProfileCreatesTheReplaceableSmtpDeliveryAdapterWithoutDispatching() {
        assertThat(emailDelivery).isInstanceOf(SmtpEmailDelivery.class);
    }
}
