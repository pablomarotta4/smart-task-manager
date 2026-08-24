package com.pablomarotta.smart_task_manager.service;

import com.pablomarotta.smart_task_manager.config.EmailOutboxProperties;
import com.pablomarotta.smart_task_manager.repository.EmailOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailOutboxTransitionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T16:00:00Z");

    @Mock
    private EmailOutboxRepository emailOutboxRepository;

    @Test
    void retriesAFailedCurrentClaimWithBoundedExponentialBackoffAndNoErrorMetadata() {
        EmailOutboxProperties properties = properties(3);
        EmailOutboxTransitionService service = new EmailOutboxTransitionService(
                emailOutboxRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties
        );
        OutboxClaim claim = new OutboxClaim(
                UUID.randomUUID(),
                LocalDateTime.ofInstant(NOW.minusSeconds(5), ZoneOffset.UTC),
                2
        );
        when(emailOutboxRepository.releaseForRetry(
                claim.outboxId(),
                claim.claimedAt(),
                claim.attempt(),
                LocalDateTime.ofInstant(NOW.plusSeconds(20), ZoneOffset.UTC),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        )).thenReturn(1);

        service.markFailure(claim, "SMTP_DELIVERY_FAILED");

        verify(emailOutboxRepository).releaseForRetry(
                claim.outboxId(),
                claim.claimedAt(),
                claim.attempt(),
                LocalDateTime.ofInstant(NOW.plusSeconds(20), ZoneOffset.UTC),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void marksTheMaximumAttemptDeadWithOnlyAStableFailureCode() {
        EmailOutboxProperties properties = properties(3);
        EmailOutboxTransitionService service = new EmailOutboxTransitionService(
                emailOutboxRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties
        );
        OutboxClaim claim = new OutboxClaim(
                UUID.randomUUID(),
                LocalDateTime.ofInstant(NOW.minusSeconds(5), ZoneOffset.UTC),
                3
        );
        when(emailOutboxRepository.markDead(
                claim.outboxId(),
                claim.claimedAt(),
                claim.attempt(),
                "SMTP_DELIVERY_FAILED",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        )).thenReturn(1);

        service.markFailure(claim, "SMTP_DELIVERY_FAILED");

        verify(emailOutboxRepository).markDead(
                claim.outboxId(),
                claim.claimedAt(),
                claim.attempt(),
                "SMTP_DELIVERY_FAILED",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsAZeroStaleClaimLeaseThatWouldAllowAnotherNodeToStealActiveDelivery() {
        EmailOutboxProperties properties = properties(3);
        properties.setStaleClaimAfter(Duration.ZERO);

        assertThat(Validation.buildDefaultValidatorFactory()
                .getValidator()
                .validate(properties))
                .isNotEmpty();
    }

    @Test
    void rejectsACombinedSmtpTimeoutBudgetThatCouldOutliveTheDeliveryClaimLease() {
        EmailOutboxProperties properties = properties(3);
        properties.setStaleClaimAfter(Duration.ofSeconds(25));
        properties.setSmtpConnectionTimeout(Duration.ofSeconds(10));
        properties.setSmtpReadTimeout(Duration.ofSeconds(10));
        properties.setSmtpWriteTimeout(Duration.ofSeconds(10));

        assertThat(Validation.buildDefaultValidatorFactory()
                .getValidator()
                .validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("deliveryTimeout"));
    }

    @Test
    void rejectsAnHttpLinkWhenSecureLinksAreRequired() {
        EmailOutboxProperties properties = properties(3);
        properties.setRequireSecureLinks(true);
        properties.setLinkBaseUrl("http://tasks.example.test");

        assertThat(Validation.buildDefaultValidatorFactory()
                .getValidator()
                .validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("linkBaseUrlAllowed"));
    }

    @Test
    void rejectsSecureLinksWithUserInfoQueryOrFragment() {
        for (String linkBaseUrl : Stream.of(
                "https://user@tasks.example.test",
                "https://tasks.example.test?campaign=mail",
                "https://tasks.example.test#existing"
        ).toList()) {
            EmailOutboxProperties properties = properties(3);
            properties.setRequireSecureLinks(true);
            properties.setLinkBaseUrl(linkBaseUrl);

            assertThat(Validation.buildDefaultValidatorFactory()
                    .getValidator()
                    .validate(properties))
                    .anyMatch(violation -> violation.getPropertyPath().toString().contains("linkBaseUrlAllowed"));
        }
    }

    private EmailOutboxProperties properties(int maxAttempts) {
        EmailOutboxProperties properties = new EmailOutboxProperties();
        properties.setMaximumAttempts(maxAttempts);
        properties.setRetryInitialDelay(Duration.ofSeconds(10));
        properties.setRetryMaximumDelay(Duration.ofMinutes(5));
        return properties;
    }
}
