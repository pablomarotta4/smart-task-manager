package com.pablomarotta.smart_task_manager.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class EmailOutboxMailConfigurationTest {

    @Test
    void configuresBoundedSmtpTimeoutsForDefaultAndProductionProfiles() {
        assertTimeouts(readProperties("application.yaml"));
        assertTimeouts(readProperties("application-prod.yaml"));
    }

    @Test
    void productionRequiresStartTlsForAccountActionEmail() {
        Properties productionProperties = readProperties("application-prod.yaml");

        assertThat(productionProperties)
                .containsEntry("spring.mail.properties.mail.smtp.starttls.enable", true)
                .containsEntry("spring.mail.properties.mail.smtp.starttls.required", true)
                .containsEntry("email-outbox.require-secure-links", true)
                .containsEntry("email-outbox.smtp-connection-timeout", "${EMAIL_SMTP_CONNECTION_TIMEOUT_MS:5000}")
                .containsEntry("email-outbox.smtp-read-timeout", "${EMAIL_SMTP_READ_TIMEOUT_MS:10000}")
                .containsEntry("email-outbox.smtp-write-timeout", "${EMAIL_SMTP_WRITE_TIMEOUT_MS:10000}");
    }

    private void assertTimeouts(Properties properties) {
        assertThat(properties)
                .containsEntry("spring.mail.properties.mail.smtp.connectiontimeout", "${EMAIL_SMTP_CONNECTION_TIMEOUT_MS:5000}")
                .containsEntry("spring.mail.properties.mail.smtp.timeout", "${EMAIL_SMTP_READ_TIMEOUT_MS:10000}")
                .containsEntry("spring.mail.properties.mail.smtp.writetimeout", "${EMAIL_SMTP_WRITE_TIMEOUT_MS:10000}");
    }

    private Properties readProperties(String resourceName) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resourceName));
        return factory.getObject();
    }
}
