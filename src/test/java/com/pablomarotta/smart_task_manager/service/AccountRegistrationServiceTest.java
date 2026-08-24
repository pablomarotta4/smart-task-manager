package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.UserRequest;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRegistrationServiceTest {

    @Mock
    private AccountActionService accountActionService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AccountRegistrationService accountRegistrationService;

    @Test
    void registersTheUserThenEnqueuesVerificationWithinTheOrchestrationBoundary() {
        UserRequest request = new UserRequest();
        UserResponse createdUser = new UserResponse();
        createdUser.setUsername("alice");
        when(userService.createUser(request)).thenReturn(createdUser);

        UserResponse result = accountRegistrationService.register(request);

        assertThat(result).isSameAs(createdUser);
        verify(accountActionService).enqueueEmailVerification("alice");
    }

    @Test
    void propagatesVerificationEnqueueFailureSoTheOuterTransactionRollsBackTheUserCreation() {
        UserRequest request = new UserRequest();
        UserResponse createdUser = new UserResponse();
        createdUser.setUsername("alice");
        when(userService.createUser(any())).thenReturn(createdUser);
        doThrow(new IllegalStateException("outbox persistence failed"))
                .when(accountActionService).enqueueEmailVerification("alice");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accountRegistrationService.register(request))
                .isInstanceOf(IllegalStateException.class);

        verify(userService).createUser(request);
        verify(accountActionService).enqueueEmailVerification("alice");
    }
}
