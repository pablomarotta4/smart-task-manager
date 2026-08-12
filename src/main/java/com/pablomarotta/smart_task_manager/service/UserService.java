package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.UserRequest;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.exception.UserDuplicatedException;
import com.pablomarotta.smart_task_manager.exception.UserNotFoundException;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public UserResponse createUser(UserRequest userRequest) {
        String normalizedEmail = normalizeEmail(userRequest.getEmail());
        if (userRepository.existsByUsername(userRequest.getUsername())
                || userRepository.existsByEmailNormalized(normalizedEmail)) {
            throw new UserDuplicatedException("User already exists with provided username or email");
        }

        if (userRequest.getPassword() == null || userRequest.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }

        User user = User.builder()
                .username(userRequest.getUsername())
                .email(userRequest.getEmail().strip())
                .emailNormalized(normalizedEmail)
                .fullName(userRequest.getFullName())
                .password(passwordEncoder.encode(userRequest.getPassword()))
                .build();

        User savedUser = userRepository.save(user);

        return mapToResponse(savedUser);
    }

    private UserResponse mapToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setRole(user.getRole() != null ? user.getRole().name() : null);
        response.setActive(user.getActive());
        response.setEmailVerified(user.getVerifiedAt() != null);
        response.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        response.setUpdatedAt(user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null);
        return response;
    }

    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
        return mapToResponse(user);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse updateUser(String username, UserRequest userRequest) {
        boolean passwordChanged = userRequest.getPassword() != null && !userRequest.getPassword().isBlank();
        User user = userRepository.findActiveForUpdateByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

        if (!Objects.equals(user.getUsername(), userRequest.getUsername())) {
            throw new IllegalArgumentException("Username cannot be changed");
        }

        if (!Objects.equals(user.getEmail(), userRequest.getEmail())) {
            throw new IllegalArgumentException("Email cannot be changed");
        }

        user.setFullName(userRequest.getFullName());
        if (passwordChanged) {
            user.setPassword(passwordEncoder.encode(userRequest.getPassword()));
            incrementAuthVersion(user);
        }

        User updatedUser = userRepository.save(user);
        if (passwordChanged) {
            refreshTokenService.revokeAllForUserId(user.getId());
        }
        return mapToResponse(updatedUser);
    }

    @Transactional
    public void deleteUser(String username) {
        User user = userRepository.findActiveForUpdateByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

        user.setActive(false);
        incrementAuthVersion(user);
        userRepository.save(user);
        refreshTokenService.revokeAllForUserId(user.getId());
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void incrementAuthVersion(User user) {
        int authVersion = user.getAuthVersion() == null ? 0 : user.getAuthVersion();
        user.setAuthVersion(authVersion + 1);
    }
}
