package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.dto.UserRequest;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.exception.UserDuplicatedException;
import com.pablomarotta.smart_task_manager.exception.UserNotFoundException;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserService userService;

    private UserRequest userRequest;
    private User user;

    @BeforeEach
    void setUp() {
        userRequest = new UserRequest();
        userRequest.setUsername("testuser");
        userRequest.setEmail("test@example.com");
        userRequest.setPassword("password123");
        userRequest.setFullName("Test User");

        user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .emailNormalized("test@example.com")
                .password("$2a$10$encodedPassword")
                .fullName("Test User")
                .authVersion(0)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should create user successfully")
    void testCreateUser_Success() {
        // Arrange
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmailNormalized(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        UserResponse response = userService.createUser(userRequest);

        // Assert
        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("Test User", response.getFullName());
        assertTrue(response.getActive());

        verify(userRepository, times(1)).existsByUsername("testuser");
        verify(userRepository, times(1)).existsByEmailNormalized("test@example.com");
        verify(passwordEncoder, times(1)).encode("password123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should encrypt password when creating user")
    void testCreateUser_PasswordEncryption() {
        // Arrange
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmailNormalized(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // Act
        userService.createUser(userRequest);

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("$2a$10$encodedPassword", savedUser.getPassword());
        assertNotEquals("password123", savedUser.getPassword());
    }

    @Test
    void createUserNormalizesEmailAndLeavesItUnverified() {
        userRequest.setEmail("  Test@Example.COM  ");
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmailNormalized("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.createUser(userRequest);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals("Test@Example.COM", savedUser.getValue().getEmail());
        assertEquals("test@example.com", savedUser.getValue().getEmailNormalized());
        assertNull(savedUser.getValue().getVerifiedAt());
        assertFalse(response.getEmailVerified());
    }

    @Test
    void userLifecycleNormalizesEmailForDirectRepositoryPersistence() {
        User directUser = User.builder()
                .email("  Direct@Example.COM ")
                .build();

        directUser.normalizeEmail();

        assertEquals("Direct@Example.COM", directUser.getEmail());
        assertEquals("direct@example.com", directUser.getEmailNormalized());
    }

    @Test
    @DisplayName("Should throw exception when username already exists")
    void testCreateUser_DuplicateUsername() {
        // Arrange
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        // Act & Assert
        UserDuplicatedException exception = assertThrows(
                UserDuplicatedException.class,
                () -> userService.createUser(userRequest)
        );

        assertEquals("User already exists with provided username or email", exception.getMessage());
        verify(userRepository, times(1)).existsByUsername("testuser");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void testCreateUser_DuplicateEmail() {
        // Arrange
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmailNormalized("test@example.com")).thenReturn(true);

        // Act & Assert
        UserDuplicatedException exception = assertThrows(
                UserDuplicatedException.class,
                () -> userService.createUser(userRequest)
        );

        assertEquals("User already exists with provided username or email", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should get user by username successfully")
    void testGetUserByUsername_Success() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // Act
        UserResponse response = userService.getUserByUsername("testuser");

        // Assert
        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        verify(userRepository, times(1)).findByUsername("testuser");
    }

    @Test
    @DisplayName("Should throw exception when user not found by username")
    void testGetUserByUsername_NotFound() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.getUserByUsername("nonexistent")
        );

        assertEquals("User not found with username: nonexistent", exception.getMessage());
        verify(userRepository, times(1)).findByUsername("nonexistent");
    }

    @Test
    @DisplayName("Should get all users successfully")
    void testGetAllUsers_Success() {
        // Arrange
        User user2 = User.builder()
                .id(2L)
                .username("testuser2")
                .email("test2@example.com")
                .password("$2a$10$encodedPassword2")
                .fullName("Test User 2")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findAll()).thenReturn(Arrays.asList(user, user2));

        // Act
        List<UserResponse> responses = userService.getAllUsers();

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("testuser", responses.get(0).getUsername());
        assertEquals("testuser2", responses.get(1).getUsername());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should update the full name without changing identity")
    void testUpdateUser_Success() {
        // Arrange
        UserRequest updateRequest = new UserRequest();
        updateRequest.setUsername("testuser");
        updateRequest.setEmail("test@example.com");
        updateRequest.setPassword(null);
        updateRequest.setFullName("Updated User");

        User updatedUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("$2a$10$encodedPassword")
                .fullName("Updated User")
                .active(true)
                .createdAt(user.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findActiveForUpdateByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        // Act
        UserResponse response = userService.updateUser("testuser", updateRequest);

        // Assert
        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("Updated User", response.getFullName());
        verify(userRepository, times(1)).findActiveForUpdateByUsername("testuser");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should encrypt password when updating user")
    void testUpdateUser_PasswordEncryption() {
        // Arrange
        UserRequest updateRequest = new UserRequest();
        updateRequest.setUsername("testuser");
        updateRequest.setEmail("test@example.com");
        updateRequest.setPassword("newPassword123");
        updateRequest.setFullName("Test User");

        when(userRepository.findActiveForUpdateByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("$2a$10$newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // Act
        userService.updateUser("testuser", updateRequest);

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("$2a$10$newEncodedPassword", savedUser.getPassword());
        assertEquals(1, savedUser.getAuthVersion());
        verify(passwordEncoder, times(1)).encode("newPassword123");
        verify(userRepository).findActiveForUpdateByUsername("testuser");
        verify(refreshTokenService).revokeAllForUserId(1L);
    }

    @Test
    void updateUserRejectsUsernameChanges() {
        UserRequest updateRequest = new UserRequest();
        updateRequest.setUsername("another-user");
        updateRequest.setEmail("test@example.com");
        updateRequest.setFullName("Test User");
        when(userRepository.findActiveForUpdateByUsername("testuser")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> userService.updateUser("testuser", updateRequest));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserRejectsEmailChanges() {
        UserRequest updateRequest = new UserRequest();
        updateRequest.setUsername("testuser");
        updateRequest.setEmail("other@example.com");
        updateRequest.setFullName("Test User");
        when(userRepository.findActiveForUpdateByUsername("testuser")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> userService.updateUser("testuser", updateRequest));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should not update password when not provided")
    void testUpdateUser_NoPasswordChange() {
        // Arrange
        UserRequest updateRequest = new UserRequest();
        updateRequest.setUsername("testuser");
        updateRequest.setEmail("test@example.com");
        updateRequest.setPassword(null);
        updateRequest.setFullName("Test User Updated");

        String originalPassword = user.getPassword();

        when(userRepository.findActiveForUpdateByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        userService.updateUser("testuser", updateRequest);

        // Assert
        verify(passwordEncoder, never()).encode(anyString());
        assertEquals(originalPassword, user.getPassword());
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent user")
    void testUpdateUser_UserNotFound() {
        // Arrange
        when(userRepository.findActiveForUpdateByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.updateUser("nonexistent", userRequest)
        );

        assertEquals("User not found with username: nonexistent", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject username changes before checking for duplicates")
    void testUpdateUser_DuplicateUsername() {
        // Arrange
        UserRequest updateRequest = new UserRequest();
        updateRequest.setUsername("duplicateuser");
        updateRequest.setEmail("test@example.com");
        updateRequest.setFullName("Test User");

        when(userRepository.findActiveForUpdateByUsername("testuser")).thenReturn(Optional.of(user));
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateUser("testuser", updateRequest)
        );

        assertEquals("Username cannot be changed", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).existsByUsername(anyString());
    }

    @Test
    @DisplayName("Should delete user successfully (soft delete)")
    void testDeleteUser_Success() {
        // Arrange
        when(userRepository.findActiveForUpdateByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // Act
        userService.deleteUser("testuser");

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertFalse(savedUser.getActive());
        assertEquals(1, savedUser.getAuthVersion());
        verify(userRepository, times(1)).findActiveForUpdateByUsername("testuser");
        verify(userRepository, times(1)).save(any(User.class));
        verify(refreshTokenService).revokeAllForUserId(1L);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent user")
    void testDeleteUser_UserNotFound() {
        // Arrange
        when(userRepository.findActiveForUpdateByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.deleteUser("nonexistent")
        );

        assertEquals("User not found with username: nonexistent", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }
}
