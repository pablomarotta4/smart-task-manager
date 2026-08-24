package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.exception.AccountActionErrorCode;
import com.pablomarotta.smart_task_manager.exception.AccountActionException;
import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;
import com.pablomarotta.smart_task_manager.model.AccountActionRequest;
import com.pablomarotta.smart_task_manager.model.AccountActionState;
import com.pablomarotta.smart_task_manager.model.EmailOutbox;
import com.pablomarotta.smart_task_manager.model.EmailOutboxKind;
import com.pablomarotta.smart_task_manager.model.EmailOutboxState;
import com.pablomarotta.smart_task_manager.model.User;
import com.pablomarotta.smart_task_manager.repository.AccountActionRequestRepository;
import com.pablomarotta.smart_task_manager.repository.EmailOutboxRepository;
import com.pablomarotta.smart_task_manager.repository.UserRepository;
import com.pablomarotta.smart_task_manager.security.ActionTokenCodec;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountActionService {

    private static final int TOKEN_VERSION = 1;

    private final ActionTokenCodec actionTokenCodec;
    private final AccountActionRequestRepository accountActionRequestRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final Clock clock;
    private final long verificationExpirationMs;
    private final long resetExpirationMs;

    public AccountActionService(
            ActionTokenCodec actionTokenCodec,
            AccountActionRequestRepository accountActionRequestRepository,
            EmailOutboxRepository emailOutboxRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            Clock clock,
            @Value("${account-action.verification-expiration-ms:86400000}") long verificationExpirationMs,
            @Value("${account-action.password-reset-expiration-ms:1800000}") long resetExpirationMs
    ) {
        this.actionTokenCodec = actionTokenCodec;
        this.accountActionRequestRepository = accountActionRequestRepository;
        this.emailOutboxRepository = emailOutboxRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.clock = clock;
        this.verificationExpirationMs = verificationExpirationMs;
        this.resetExpirationMs = resetExpirationMs;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findActiveForUpdateByEmailNormalized(normalizeEmail(email))
                .ifPresent(user -> issueLocked(user, AccountActionPurpose.RESET_PASSWORD));
    }

    @Transactional
    public void enqueueEmailVerification(String username) {
        userRepository.findActiveForUpdateByUsername(username)
                .filter(user -> user.getVerifiedAt() == null)
                .ifPresent(user -> issueLocked(user, AccountActionPurpose.VERIFY_EMAIL));
    }

    @Transactional
    public void resendEmailVerification(String username) {
        enqueueEmailVerification(username);
    }

    @Transactional
    public void confirmPasswordReset(String compactToken, String password) {
        ClaimedAction claimedAction = claim(compactToken, AccountActionPurpose.RESET_PASSWORD);
        AccountActionRequest action = claimedAction.action();
        User user = claimedAction.user();
        user.setPassword(passwordEncoder.encode(password));
        user.setAuthVersion(authVersionOf(user) + 1);
        userRepository.save(user);
        refreshTokenService.revokeAllForUserId(user.getId());
        consume(action);
    }

    @Transactional
    public void confirmEmailVerification(String compactToken) {
        ClaimedAction claimedAction = claim(compactToken, AccountActionPurpose.VERIFY_EMAIL);
        AccountActionRequest action = claimedAction.action();
        User user = claimedAction.user();
        user.setVerifiedAt(now());
        userRepository.save(user);
        consume(action);
    }

    private void issueLocked(User user, AccountActionPurpose purpose) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plusMillis(expirationFor(purpose));
        LocalDateTime issuedAtUtc = localDateTime(issuedAt);
        UUID actionId = UUID.randomUUID();
        String compactToken = actionTokenCodec.encode(actionId, purpose, TOKEN_VERSION, issuedAt, expiresAt);
        AccountActionRequest action = AccountActionRequest.builder()
                .id(actionId)
                .user(user)
                .purpose(purpose)
                .state(AccountActionState.PENDING)
                .tokenHash(actionTokenCodec.hash(compactToken))
                .tokenVersion(TOKEN_VERSION)
                .issuedAt(issuedAtUtc)
                .expiresAt(localDateTime(expiresAt))
                .build();
        accountActionRequestRepository.invalidatePendingByUserIdAndPurpose(user.getId(), purpose, issuedAtUtc);
        accountActionRequestRepository.save(action);
        emailOutboxRepository.save(EmailOutbox.builder()
                .id(UUID.randomUUID())
                .recipient(user)
                .accountActionRequest(action)
                .kind(EmailOutboxKind.ACCOUNT_ACTION)
                .purpose(purpose)
                .state(EmailOutboxState.PENDING)
                .attempts(0)
                .availableAt(issuedAtUtc)
                .build());
    }

    private ClaimedAction claim(String compactToken, AccountActionPurpose expectedPurpose) {
        ActionTokenCodec.DecodedActionToken decoded = decode(compactToken, expectedPurpose);
        String tokenHash = actionTokenCodec.hash(compactToken);
        Long userId = accountActionRequestRepository.findUserIdByIdAndTokenHash(decoded.actionId(), tokenHash)
                .orElseThrow(() -> actionFailure(AccountActionErrorCode.ACCOUNT_ACTION_INVALID));
        User user = userRepository.findActiveForUpdateById(userId)
                .orElseThrow(() -> actionFailure(AccountActionErrorCode.ACCOUNT_ACTION_INVALID));
        AccountActionRequest action = accountActionRequestRepository
                .findForUpdateByIdAndTokenHash(decoded.actionId(), tokenHash)
                .orElseThrow(() -> actionFailure(AccountActionErrorCode.ACCOUNT_ACTION_INVALID));
        validateClaim(action, userId, decoded, tokenHash, expectedPurpose);
        return new ClaimedAction(action, user);
    }

    private ActionTokenCodec.DecodedActionToken decode(String compactToken, AccountActionPurpose expectedPurpose) {
        try {
            return actionTokenCodec.decode(compactToken, expectedPurpose);
        } catch (ExpiredJwtException exception) {
            throw actionFailure(AccountActionErrorCode.ACCOUNT_ACTION_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw actionFailure(AccountActionErrorCode.ACCOUNT_ACTION_INVALID);
        }
    }

    private void validateClaim(
            AccountActionRequest action,
            Long userId,
            ActionTokenCodec.DecodedActionToken decoded,
            String tokenHash,
            AccountActionPurpose expectedPurpose
    ) {
        if (!userId.equals(action.getUserId())
                || action.getPurpose() != expectedPurpose
                || decoded.purpose() != expectedPurpose
                || action.getTokenVersion() != decoded.tokenVersion()
                || !tokenHash.equals(action.getTokenHash())) {
            throw actionFailure(AccountActionErrorCode.ACCOUNT_ACTION_INVALID);
        }
        if (!action.getExpiresAt().isAfter(now())) {
            throw actionFailure(AccountActionErrorCode.ACCOUNT_ACTION_EXPIRED);
        }
        if (action.getState() == AccountActionState.CONSUMED) {
            throw actionFailure(AccountActionErrorCode.ACCOUNT_ACTION_USED);
        }
        if (action.getState() == AccountActionState.INVALIDATED) {
            throw actionFailure(AccountActionErrorCode.ACCOUNT_ACTION_SUPERSEDED);
        }
    }

    private void consume(AccountActionRequest action) {
        action.setState(AccountActionState.CONSUMED);
        action.setConsumedAt(now());
        accountActionRequestRepository.save(action);
    }

    private long expirationFor(AccountActionPurpose purpose) {
        return purpose == AccountActionPurpose.VERIFY_EMAIL ? verificationExpirationMs : resetExpirationMs;
    }

    private int authVersionOf(User user) {
        return user.getAuthVersion() == null ? 0 : user.getAuthVersion();
    }

    private LocalDateTime now() {
        return localDateTime(Instant.now(clock));
    }

    private LocalDateTime localDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private AccountActionException actionFailure(AccountActionErrorCode code) {
        return new AccountActionException(code);
    }

    private record ClaimedAction(AccountActionRequest action, User user) {
    }
}
