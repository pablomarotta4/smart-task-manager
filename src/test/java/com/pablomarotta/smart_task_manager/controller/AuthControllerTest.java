package com.pablomarotta.smart_task_manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablomarotta.smart_task_manager.dto.LoginRequest;
import com.pablomarotta.smart_task_manager.dto.PasswordResetConfirmRequest;
import com.pablomarotta.smart_task_manager.dto.PasswordResetRequest;
import com.pablomarotta.smart_task_manager.dto.EmailVerificationConfirmRequest;
import com.pablomarotta.smart_task_manager.dto.RegisterRequest;
import com.pablomarotta.smart_task_manager.dto.UserResponse;
import com.pablomarotta.smart_task_manager.exception.AccountActionErrorCode;
import com.pablomarotta.smart_task_manager.exception.AccountActionException;
import com.pablomarotta.smart_task_manager.exception.GlobalExceptionHandler;
import com.pablomarotta.smart_task_manager.exception.UserDuplicatedException;
import com.pablomarotta.smart_task_manager.security.AuthRateLimitExceededException;
import com.pablomarotta.smart_task_manager.security.AuthRateLimiter;
import com.pablomarotta.smart_task_manager.security.ClientIpResolver;
import com.pablomarotta.smart_task_manager.security.AuthenticatedUserPrincipal;
import com.pablomarotta.smart_task_manager.security.JwtTokenProvider;
import com.pablomarotta.smart_task_manager.security.UserDetailsServiceImpl;
import com.pablomarotta.smart_task_manager.service.AccountActionService;
import com.pablomarotta.smart_task_manager.service.AccountRegistrationService;
import com.pablomarotta.smart_task_manager.service.RefreshTokenService;
import com.pablomarotta.smart_task_manager.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    private AuthRateLimiter authRateLimiter;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @MockBean
    private AccountActionService accountActionService;

    @MockBean
    private AccountRegistrationService accountRegistrationService;

    @MockBean
    private UserService userService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void useLoopbackAddressWhenTheClientIpResolverIsNotUnderTest() {
        when(clientIpResolver.resolve(any())).thenReturn("127.0.0.1");
    }

    @Test
    void login_ShouldReturnTokenAndUser() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password");

        AuthenticatedUserPrincipal authenticatedPrincipal = authenticatedPrincipal(37L, "testuser", 5);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                authenticatedPrincipal,
                null,
                authenticatedPrincipal.getAuthorities()
        );
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(clientIpResolver.resolve(any())).thenReturn("198.51.100.10");
        when(jwtTokenProvider.generateToken("testuser", 37L, 5)).thenReturn("token");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setUsername("testuser");
        userResponse.setEmail("test@example.com");
        userResponse.setFullName("Test User");
        userResponse.setRole("USER");
        when(userService.getUserByUsername("testuser")).thenReturn(userResponse);
        when(refreshTokenService.issueForPrincipal(authenticatedPrincipal))
                .thenReturn(issuedRefreshToken("refresh-login", "testuser", 37L, 5));

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

        verify(jwtTokenProvider).generateToken("testuser", 37L, 5);
        verify(refreshTokenService).issueForPrincipal(authenticatedPrincipal);
        verify(clientIpResolver).resolve(any());
    }

    @Test
    void loginRejectsAnAuthenticationWithoutAnObservedUserIdentity() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("testuser", "password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());

        verify(refreshTokenService, never()).issueForUsername(anyString());
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

        when(accountRegistrationService.register(any())).thenReturn(userResponse);
        when(jwtTokenProvider.generateToken("newuser", 38L, 0)).thenReturn("token");
        when(refreshTokenService.issueForUsername("newuser"))
                .thenReturn(issuedRefreshToken("refresh-register", "newuser", 38L, 0));

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

        verify(accountRegistrationService).register(any());
    }

    @Test
    void passwordResetRequestAlwaysReturnsAcceptedForTheGenericPublicResponse() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("unknown@example.com");

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));

        verify(accountActionService).requestPasswordReset("unknown@example.com");
    }

    @Test
    void passwordResetConfirmReturnsNoContentWhenTheActionIsConsumed() throws Exception {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("action-token", "new-password");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(accountActionService).confirmPasswordReset("action-token", "new-password");
    }

    @Test
    void passwordResetConfirmReturnsTheStableSupersededActionCode() throws Exception {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("old-action-token", "new-password");
        doThrow(new AccountActionException(AccountActionErrorCode.ACCOUNT_ACTION_SUPERSEDED))
                .when(accountActionService).confirmPasswordReset("old-action-token", "new-password");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ACTION_SUPERSEDED"));
    }

    @Test
    void emailVerificationConfirmReturnsNoContentWhenTheActionIsConsumed() throws Exception {
        EmailVerificationConfirmRequest request = new EmailVerificationConfirmRequest("action-token");

        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(accountActionService).confirmEmailVerification("action-token");
    }

    @Test
    @WithMockUser(username = "testuser")
    void emailVerificationResendUsesTheAuthenticatedUsernameAndReturnsAccepted() throws Exception {
        mockMvc.perform(post("/api/auth/email-verification/resend"))
                .andExpect(status().isAccepted());

        verify(accountActionService).resendEmailVerification("testuser");
    }

    @Test
    void rateLimitIsCheckedBeforeLoginAuthentication() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password");
        doThrow(new AuthRateLimitExceededException(30)).when(authRateLimiter)
                .check(any(), anyString(), anyCollection());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.RETRY_AFTER, "30"));

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void register_WithDuplicatedUsername_ShouldReturnConflict() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("existing");
        registerRequest.setEmail("existing@example.com");
        registerRequest.setPassword("password");
        registerRequest.setFullName("Existing User");

        when(accountRegistrationService.register(any()))
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
                .thenReturn(issuedRefreshToken("replacement-refresh", "testuser", 37L, 5));
        when(jwtTokenProvider.generateToken("testuser", 37L, 5)).thenReturn("replacement-access");
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

        verify(jwtTokenProvider).generateToken("testuser", 37L, 5);
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

    private RefreshTokenService.IssuedRefreshToken issuedRefreshToken(
            String value,
            String username,
            Long userId,
            int authVersion
    ) {
        return new RefreshTokenService.IssuedRefreshToken(
                value,
                username,
                userId,
                authVersion,
                LocalDateTime.now().plusDays(7)
        );
    }

    private AuthenticatedUserPrincipal authenticatedPrincipal(Long userId, String username, int authVersion) {
        return new AuthenticatedUserPrincipal(
                userId,
                username,
                "encoded-password",
                "USER",
                authVersion,
                true
        );
    }
}
