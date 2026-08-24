package com.pablomarotta.smart_task_manager.security;

import com.pablomarotta.smart_task_manager.config.SecurityConfig;
import com.pablomarotta.smart_task_manager.controller.AuthController;
import com.pablomarotta.smart_task_manager.service.AccountActionService;
import com.pablomarotta.smart_task_manager.service.AccountRegistrationService;
import com.pablomarotta.smart_task_manager.service.RefreshTokenService;
import com.pablomarotta.smart_task_manager.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AccountActionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountActionService accountActionService;

    @MockBean
    private AccountRegistrationService accountRegistrationService;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private AuthRateLimiter authRateLimiter;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private UserService userService;

    @Test
    void passwordResetEndpointsAndVerificationConfirmationArePublic() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType("application/json")
                        .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType("application/json")
                        .content("{\"token\":\"action-token\",\"password\":\"new-password\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType("application/json")
                        .content("{\"token\":\"action-token\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void emailVerificationResendRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/email-verification/resend"))
                .andExpect(status().isUnauthorized());

        verify(accountActionService, never()).resendEmailVerification(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @WithMockUser(username = "alice")
    void authenticatedUserCanResendEmailVerification() throws Exception {
        mockMvc.perform(post("/api/auth/email-verification/resend"))
                .andExpect(status().isAccepted());

        verify(accountActionService).resendEmailVerification("alice");
    }
}
