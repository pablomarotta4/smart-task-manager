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
                .password("$2a$10$encodedPassword")
                .fullName("Test User")
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
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
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
        verify(userRepository, times(1)).existsByEmail("test@example.com");
        verify(passwordEncoder, times(1)).encode("password123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should encrypt password when creating user")
    void testCreateUser_PasswordEncryption() {
        // Arrange
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
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
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

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
    @DisplayName("Should update user successfully")
    void testUpdateUser_Success() {
        // Arrange
        UserRequest updateRequest = new UserRequest();
        updateRequest.setUsername("updateduser");
        updateRequest.setEmail("updated@example.com");
        updateRequest.setPassword("newPassword123");
        updateRequest.setFullName("Updated User");

        User updatedUser = User.builder()
                .id(1L)
                .username("updateduser")
                .email("updated@example.com")
                .password("$2a$10$newEncodedPassword")
                .fullName("Updated User")
                .active(true)
                .createdAt(user.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("updateduser")).thenReturn(false);
        when(userRepository.existsByEmail("updated@example.com")).thenReturn(false);
        when(passwordEncoder.encode("newPassword123")).thenReturn("$2a$10$newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        // Act
        UserResponse response = userService.updateUser("testuser", updateRequest);

        // Assert
        assertNotNull(response);
        assertEquals("updateduser", response.getUsername());
        assertEquals("updated@example.com", response.getEmail());
        assertEquals("Updated User", response.getFullName());
        verify(userRepository, times(1)).findByUsername("testuser");
        verify(passwordEncoder, times(1)).encode("newPassword123");
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

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("$2a$10$newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // Act
        userService.updateUser("testuser", updateRequest);

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("$2a$10$newEncodedPassword", savedUser.getPassword());
        verify(passwordEncoder, times(1)).encode("newPassword123");
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

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
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
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.updateUser("nonexistent", userRequest)
        );

        assertEquals("User not found with username: nonexistent", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when updating with duplicate username")
    void testUpdateUser_DuplicateUsername() {
        // Arrange
        UserRequest updateRequest = new UserRequest();
        updateRequest.setUsername("duplicateuser");
        updateRequest.setEmail("test@example.com");
        updateRequest.setFullName("Test User");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("duplicateuser")).thenReturn(true);

        // Act & Assert
        UserDuplicatedException exception = assertThrows(
                UserDuplicatedException.class,
                () -> userService.updateUser("testuser", updateRequest)
        );

        assertEquals("Username already exists: duplicateuser", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should delete user successfully (soft delete)")
    void testDeleteUser_Success() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // Act
        userService.deleteUser("testuser");

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertFalse(savedUser.getActive());
        verify(userRepository, times(1)).findByUsername("testuser");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent user")
    void testDeleteUser_UserNotFound() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.deleteUser("nonexistent")
        );

        assertEquals("User not found with username: nonexistent", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }
}
