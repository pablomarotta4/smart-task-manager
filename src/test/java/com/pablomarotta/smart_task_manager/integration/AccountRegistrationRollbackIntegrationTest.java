package com.pablomarotta.smart_task_manager.integration;

import com.pablomarotta.smart_task_manager.dto.UserRequest;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.service.AccountActionService;
import com.pablomarotta.smart_task_manager.service.AccountRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = "email-outbox.from-address=test@smart-task-manager.local")
class AccountRegistrationRollbackIntegrationTest extends PostgresIntegrationTest {

    @MockBean
    private AccountActionService accountActionService;

    @MockBean
    private JavaMailSender mailSender;

    private final AccountRegistrationService accountRegistrationService;
    private final UserRepository userRepository;

    @Autowired
    AccountRegistrationRollbackIntegrationTest(
            DataSource dataSource,
            AccountRegistrationService accountRegistrationService,
            UserRepository userRepository
    ) {
        super(dataSource);
        this.accountRegistrationService = accountRegistrationService;
        this.userRepository = userRepository;
    }

    @Test
    void rollsBackUserCreationWhenVerificationEnqueueFails() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "rollback" + suffix;
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("password123");
        request.setFullName("Rollback User");
        doThrow(new IllegalStateException("outbox persistence failed"))
                .when(accountActionService).enqueueEmailVerification(username);

        assertThatThrownBy(() -> accountRegistrationService.register(request))
                .isInstanceOf(IllegalStateException.class);

        assertThat(userRepository.findByUsername(username)).isEmpty();
    }
}
