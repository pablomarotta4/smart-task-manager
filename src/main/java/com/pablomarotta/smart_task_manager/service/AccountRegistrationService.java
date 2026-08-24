package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.UserRequest;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountRegistrationService {

    private final AccountActionService accountActionService;
    private final UserService userService;

    public AccountRegistrationService(AccountActionService accountActionService, UserService userService) {
        this.accountActionService = accountActionService;
        this.userService = userService;
    }

    @Transactional
    public UserResponse register(UserRequest userRequest) {
        UserResponse user = userService.createUser(userRequest);
        accountActionService.enqueueEmailVerification(user.getUsername());
        return user;
    }
}
