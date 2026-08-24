package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.exception.AccountActionErrorCode;
import com.pablomarotta.smart_task_manager.exception.AccountActionException;
import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;
import com.pablomarotta.smart_task_manager.model.AccountActionRequest;
import com.pablomarotta.smart_task_manager.model.AccountActionState;
import com.pablomarotta.smart_task_manager.model.EmailOutbox;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.AccountActionRequestRepository;
import com.pablomarotta.smart_task_manager.repository.EmailOutboxRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.security.ActionTokenCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountActionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T18:00:00Z");
    private static final long VERIFICATION_EXPIRATION_MS = 86_400_000L;
    private static final long RESET_EXPIRATION_MS = 1_800_000L;

    @Mock
    private ActionTokenCodec actionTokenCodec;

    @Mock
    private AccountActionRequestRepository accountActionRequestRepository;

    @Mock
    private EmailOutboxRepository emailOutboxRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    private AccountActionService accountActionService;
    private User activeUser;

    @BeforeEach
    void setUp() {
        accountActionService = new AccountActionService(
                actionTokenCodec,
                accountActionRequestRepository,
                emailOutboxRepository,
                passwordEncoder,
                refreshTokenService,
                userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                VERIFICATION_EXPIRATION_MS,
                RESET_EXPIRATION_MS
        );
        activeUser = User.builder()
                .id(41L)
                .username("alice")
                .email("Alice@Example.com")
                .emailNormalized("alice@example.com")
                .password("old-password")
                .fullName("Alice")
                .authVersion(2)
                .active(true)
                .build();
    }

    @Test
    void requestPasswordResetDoesNothingForAnUnknownNormalizedEmail() {
        when(userRepository.findActiveForUpdateByEmailNormalized("unknown@example.com"))
                .thenReturn(Optional.empty());

        accountActionService.requestPasswordReset("  UNKNOWN@EXAMPLE.COM ");

        verify(accountActionRequestRepository, never()).invalidatePendingByUserIdAndPurpose(any(), any(), any());
        verify(accountActionRequestRepository, never()).save(any());
        verify(emailOutboxRepository, never()).save(any());
    }

    @Test
    void requestPasswordResetInvalidatesPriorPendingActionAndPersistsOnlyItsTokenHash() {
        when(userRepository.findActiveForUpdateByEmailNormalized("alice@example.com"))
                .thenReturn(Optional.of(activeUser));
        when(actionTokenCodec.encode(any(), eq(AccountActionPurpose.RESET_PASSWORD), eq(1), eq(NOW),
                eq(NOW.plusMillis(RESET_EXPIRATION_MS)))).thenReturn("compact-action-token");
        when(actionTokenCodec.hash("compact-action-token")).thenReturn("stored-token-hash");

        accountActionService.requestPasswordReset(" Alice@Example.com ");

        ArgumentCaptor<AccountActionRequest> actionCaptor = ArgumentCaptor.forClass(AccountActionRequest.class);
        ArgumentCaptor<EmailOutbox> outboxCaptor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(accountActionRequestRepository).invalidatePendingByUserIdAndPurpose(
                41L,
                AccountActionPurpose.RESET_PASSWORD,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
        verify(accountActionRequestRepository).save(actionCaptor.capture());
        verify(emailOutboxRepository).save(outboxCaptor.capture());
        assertThat(actionCaptor.getValue().getId()).isNotNull();
        assertThat(actionCaptor.getValue().getPurpose()).isEqualTo(AccountActionPurpose.RESET_PASSWORD);
        assertThat(actionCaptor.getValue().getState()).isEqualTo(AccountActionState.PENDING);
        assertThat(actionCaptor.getValue().getTokenHash()).isEqualTo("stored-token-hash");
        assertThat(actionCaptor.getValue().getTokenHash()).doesNotContain("compact-action-token");
        assertThat(outboxCaptor.getValue().getAccountActionRequest()).isSameAs(actionCaptor.getValue());
        assertThat(outboxCaptor.getValue().getRecipient()).isSameAs(activeUser);
    }

    @Test
    void resendVerificationDoesNothingForAnAlreadyVerifiedUser() {
        activeUser.setVerifiedAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(userRepository.findActiveForUpdateByUsername("alice")).thenReturn(Optional.of(activeUser));

        accountActionService.resendEmailVerification("alice");

        verify(accountActionRequestRepository, never()).invalidatePendingByUserIdAndPurpose(any(), any(), any());
        verify(accountActionRequestRepository, never()).save(any());
        verify(emailOutboxRepository, never()).save(any());
    }

    @Test
    void confirmPasswordResetChangesCredentialsInvalidatesSessionsAndConsumesTheAction() {
        UUID actionId = UUID.randomUUID();
        AccountActionRequest action = pendingAction(actionId, AccountActionPurpose.RESET_PASSWORD);
        ActionTokenCodec.DecodedActionToken decoded = decoded(actionId, AccountActionPurpose.RESET_PASSWORD);
        when(actionTokenCodec.decode("compact-token", AccountActionPurpose.RESET_PASSWORD)).thenReturn(decoded);
        when(actionTokenCodec.hash("compact-token")).thenReturn("action-hash");
        when(accountActionRequestRepository.findUserIdByIdAndTokenHash(actionId, "action-hash"))
                .thenReturn(Optional.of(41L));
        when(userRepository.findActiveForUpdateById(41L)).thenReturn(Optional.of(activeUser));
        when(accountActionRequestRepository.findForUpdateByIdAndTokenHash(actionId, "action-hash"))
                .thenReturn(Optional.of(action));
        when(passwordEncoder.encode("new-password")).thenReturn("new-password-hash");

        accountActionService.confirmPasswordReset("compact-token", "new-password");

        assertThat(activeUser.getPassword()).isEqualTo("new-password-hash");
        assertThat(activeUser.getAuthVersion()).isEqualTo(3);
        assertThat(action.getState()).isEqualTo(AccountActionState.CONSUMED);
        assertThat(action.getConsumedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(refreshTokenService).revokeAllForUserId(41L);
        verify(userRepository).save(activeUser);
        verify(accountActionRequestRepository).save(action);
    }

    @Test
    void confirmEmailVerificationMarksTheUserVerifiedAndConsumesTheAction() {
        UUID actionId = UUID.randomUUID();
        AccountActionRequest action = pendingAction(actionId, AccountActionPurpose.VERIFY_EMAIL);
        when(actionTokenCodec.decode("verification-token", AccountActionPurpose.VERIFY_EMAIL))
                .thenReturn(decoded(actionId, AccountActionPurpose.VERIFY_EMAIL));
        when(actionTokenCodec.hash("verification-token")).thenReturn("action-hash");
        when(accountActionRequestRepository.findUserIdByIdAndTokenHash(actionId, "action-hash"))
                .thenReturn(Optional.of(41L));
        when(userRepository.findActiveForUpdateById(41L)).thenReturn(Optional.of(activeUser));
        when(accountActionRequestRepository.findForUpdateByIdAndTokenHash(actionId, "action-hash"))
                .thenReturn(Optional.of(action));

        accountActionService.confirmEmailVerification("verification-token");

        assertThat(activeUser.getVerifiedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(action.getState()).isEqualTo(AccountActionState.CONSUMED);
        verify(refreshTokenService, never()).revokeAllForUserId(any());
    }

    @Test
    void rejectsAnInvalidatedActionAsSupersededWithoutMutatingTheUser() {
        UUID actionId = UUID.randomUUID();
        AccountActionRequest action = pendingAction(actionId, AccountActionPurpose.RESET_PASSWORD);
        action.setState(AccountActionState.INVALIDATED);
        action.setInvalidatedAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(actionTokenCodec.decode("superseded-token", AccountActionPurpose.RESET_PASSWORD))
                .thenReturn(decoded(actionId, AccountActionPurpose.RESET_PASSWORD));
        when(actionTokenCodec.hash("superseded-token")).thenReturn("action-hash");
        when(accountActionRequestRepository.findUserIdByIdAndTokenHash(actionId, "action-hash"))
                .thenReturn(Optional.of(41L));
        when(userRepository.findActiveForUpdateById(41L)).thenReturn(Optional.of(activeUser));
        when(accountActionRequestRepository.findForUpdateByIdAndTokenHash(actionId, "action-hash"))
                .thenReturn(Optional.of(action));

        assertThatThrownBy(() -> accountActionService.confirmPasswordReset("superseded-token", "new-password"))
                .isInstanceOf(AccountActionException.class)
                .extracting(exception -> ((AccountActionException) exception).getCode())
                .isEqualTo(AccountActionErrorCode.ACCOUNT_ACTION_SUPERSEDED);

        verify(passwordEncoder, never()).encode(any());
        verify(refreshTokenService, never()).revokeAllForUserId(any());
    }

    @Test
    void rejectsAnExpiredActionBeforeChangingThePassword() {
        UUID actionId = UUID.randomUUID();
        AccountActionRequest action = pendingAction(actionId, AccountActionPurpose.RESET_PASSWORD);
        action.setExpiresAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        prepareClaim("expired-token", actionId, action, AccountActionPurpose.RESET_PASSWORD);

        assertThatThrownBy(() -> accountActionService.confirmPasswordReset("expired-token", "new-password"))
                .isInstanceOf(AccountActionException.class)
                .extracting(exception -> ((AccountActionException) exception).getCode())
                .isEqualTo(AccountActionErrorCode.ACCOUNT_ACTION_EXPIRED);

        verify(passwordEncoder, never()).encode(any());
        verify(refreshTokenService, never()).revokeAllForUserId(any());
    }

    @Test
    void rejectsAConsumedActionAsAlreadyUsed() {
        UUID actionId = UUID.randomUUID();
        AccountActionRequest action = pendingAction(actionId, AccountActionPurpose.VERIFY_EMAIL);
        action.setState(AccountActionState.CONSUMED);
        action.setConsumedAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        prepareClaim("used-token", actionId, action, AccountActionPurpose.VERIFY_EMAIL);

        assertThatThrownBy(() -> accountActionService.confirmEmailVerification("used-token"))
                .isInstanceOf(AccountActionException.class)
                .extracting(exception -> ((AccountActionException) exception).getCode())
                .isEqualTo(AccountActionErrorCode.ACCOUNT_ACTION_USED);
    }

    @Test
    void mapsPurposeDecodeFailureToTheStableInvalidActionCode() {
        when(actionTokenCodec.decode("wrong-purpose-token", AccountActionPurpose.RESET_PASSWORD))
                .thenThrow(new io.jsonwebtoken.JwtException("wrong purpose"));

        assertThatThrownBy(() -> accountActionService.confirmPasswordReset("wrong-purpose-token", "new-password"))
                .isInstanceOf(AccountActionException.class)
                .extracting(exception -> ((AccountActionException) exception).getCode())
                .isEqualTo(AccountActionErrorCode.ACCOUNT_ACTION_INVALID);

        verify(accountActionRequestRepository, never()).findUserIdByIdAndTokenHash(any(), any());
    }

    private void prepareClaim(
            String compactToken,
            UUID actionId,
            AccountActionRequest action,
            AccountActionPurpose purpose
    ) {
        when(actionTokenCodec.decode(compactToken, purpose)).thenReturn(decoded(actionId, purpose));
        when(actionTokenCodec.hash(compactToken)).thenReturn("action-hash");
        when(accountActionRequestRepository.findUserIdByIdAndTokenHash(actionId, "action-hash"))
                .thenReturn(Optional.of(41L));
        when(userRepository.findActiveForUpdateById(41L)).thenReturn(Optional.of(activeUser));
        when(accountActionRequestRepository.findForUpdateByIdAndTokenHash(actionId, "action-hash"))
                .thenReturn(Optional.of(action));
    }

    private AccountActionRequest pendingAction(UUID actionId, AccountActionPurpose purpose) {
        return AccountActionRequest.builder()
                .id(actionId)
                .user(activeUser)
                .userId(activeUser.getId())
                .purpose(purpose)
                .state(AccountActionState.PENDING)
                .tokenHash("action-hash")
                .tokenVersion(1)
                .issuedAt(LocalDateTime.ofInstant(NOW.minusSeconds(30), ZoneOffset.UTC))
                .expiresAt(LocalDateTime.ofInstant(NOW.plusSeconds(300), ZoneOffset.UTC))
                .build();
    }

    private ActionTokenCodec.DecodedActionToken decoded(UUID actionId, AccountActionPurpose purpose) {
        return new ActionTokenCodec.DecodedActionToken(
                actionId,
                purpose,
                1,
                NOW.minusSeconds(30),
                NOW.plusSeconds(300),
                actionId.toString()
        );
    }
}
