package com.pablomarotta.smart_task_manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.LoginRequest;
import com.pablomarotta.smart_task_manager.dto.RegisterRequest;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.exception.GlobalExceptionHandler;
import com.pablomarotta.smart_task_manager.exception.UserDuplicatedException;
import com.pablomarotta.smart_task_manager.security.JwtTokenProvider;
import com.pablomarotta.smart_task_manager.security.UserDetailsServiceImpl;
import com.pablomarotta.smart_task_manager.service.RefreshTokenService;
import com.pablomarotta.smart_task_manager.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.allOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserService userService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void login_ShouldReturnTokenAndUser() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password");

        Authentication authentication = new UsernamePasswordAuthenticationToken("testuser", "password");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtTokenProvider.generateToken("testuser")).thenReturn("token");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setUsername("testuser");
        userResponse.setEmail("test@example.com");
        userResponse.setFullName("Test User");
        userResponse.setRole("USER");
        when(userService.getUserByUsername("testuser")).thenReturn(userResponse);
        when(refreshTokenService.issueForUsername("testuser"))
                .thenReturn(issuedRefreshToken("refresh-login", "testuser"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refreshToken=refresh-login"),
                                containsString("Path=/api/auth"),
                                containsString("HttpOnly"),
                                containsString("SameSite=Strict")
                        )
                ))
                .andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("testuser"));
    }

    @Test
    void login_WithInvalidCredentials_ShouldReturnUnauthorized() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("wrong");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_ShouldCreateUserAndReturnToken() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("newuser");
        registerRequest.setEmail("new@example.com");
        registerRequest.setPassword("password");
        registerRequest.setFullName("New User");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(2L);
        userResponse.setUsername("newuser");
        userResponse.setEmail("new@example.com");
        userResponse.setFullName("New User");
        userResponse.setRole("USER");

        when(userService.createUser(any())).thenReturn(userResponse);
        when(jwtTokenProvider.generateToken("newuser")).thenReturn("token");
        when(refreshTokenService.issueForUsername("newuser"))
                .thenReturn(issuedRefreshToken("refresh-register", "newuser"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("refreshToken=refresh-register")
                ))
                .andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.user.username").value("newuser"));
    }

    @Test
    void register_WithDuplicatedUsername_ShouldReturnConflict() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("existing");
        registerRequest.setEmail("existing@example.com");
        registerRequest.setPassword("password");
        registerRequest.setFullName("Existing User");

        when(userService.createUser(any()))
                .thenThrow(new UserDuplicatedException("User already exists"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_WithShortPassword_ShouldReturnBadRequest() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("newuser");
        registerRequest.setEmail("new@example.com");
        registerRequest.setPassword("short");
        registerRequest.setFullName("New User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password: Password must be between 8 and 72 characters"));
    }

    @Test
    void refresh_ShouldRotateCookieAndReturnANewAccessToken() throws Exception {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setUsername("testuser");
        userResponse.setEmail("test@example.com");
        userResponse.setFullName("Test User");
        userResponse.setRole("USER");
        when(refreshTokenService.rotate("current-refresh"))
                .thenReturn(issuedRefreshToken("replacement-refresh", "testuser"));
        when(jwtTokenProvider.generateToken("testuser")).thenReturn("replacement-access");
        when(userService.getUserByUsername("testuser")).thenReturn(userResponse);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", "current-refresh")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("refreshToken=replacement-refresh")
                ))
                .andExpect(jsonPath("$.token").value("replacement-access"))
                .andExpect(jsonPath("$.user.username").value("testuser"));
    }

    @Test
    void refresh_WithoutCookie_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token is invalid or expired"));
    }

    @Test
    void logout_ShouldRevokeTheCookieAndClearIt() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refreshToken", "current-refresh")))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refreshToken="),
                                containsString("Max-Age=0"),
                                containsString("Path=/api/auth"),
                                containsString("HttpOnly")
                        )
                ));

        verify(refreshTokenService).revoke("current-refresh");
    }

    @Test
    @WithMockUser(username = "testuser")
    void me_WithValidAuthentication_ShouldReturnUser() throws Exception {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setUsername("testuser");
        userResponse.setEmail("test@example.com");
        userResponse.setFullName("Test User");
        userResponse.setRole("USER");

        when(userService.getUserByUsername("testuser")).thenReturn(userResponse);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    private RefreshTokenService.IssuedRefreshToken issuedRefreshToken(String value, String username) {
        return new RefreshTokenService.IssuedRefreshToken(
                value,
                username,
                LocalDateTime.now().plusDays(7)
        );
    }
}
