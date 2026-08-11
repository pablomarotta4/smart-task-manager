package com.pablomarotta.smart_task_manager.controller;

import com.pablomarotta.smart_task_manager.dto.UserRequest;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getAllUsers() {
        log.info("Retrieving all users");
        return userService.getAllUsers();
    }

    @GetMapping("/username/{username}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("#username == authentication.name or hasRole('ADMIN')")
    public UserResponse getUserByUsername(@PathVariable String username) {
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        log.info("Retrieving user with username: {}", username);
        return userService.getUserByUsername(username);
    }

    @PutMapping("/{username}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("#username == authentication.name")
    public UserResponse updateUser(@PathVariable String username, @Valid @RequestBody UserRequest userRequest) {
        log.info("Updating user with username: {}", username);
        return userService.updateUser(username, userRequest);
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("#username == authentication.name")
    public void deleteUser(@PathVariable String username) {
        log.info("Deactivating user with username: {}", username);
        userService.deleteUser(username);
    }

}
