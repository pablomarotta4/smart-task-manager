package com.pablomarotta.smart_task_manager.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "email-outbox")
public class EmailOutboxProperties {

    private boolean enabled = true;

    @NotBlank
    private String linkBaseUrl = "http://localhost:3000";

    @NotBlank
    private String fromAddress = "no-reply@smart-task-manager.local";

    private boolean requireSecureLinks;

    @Min(1)
    private int batchSize = 20;

    @Min(1)
    private int maximumAttempts = 5;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration staleClaimAfter = Duration.ofMinutes(5);

    @NotNull
    @DurationMin(seconds = 1)
    private Duration retryInitialDelay = Duration.ofSeconds(10);

    @NotNull
    @DurationMin(seconds = 1)
    private Duration retryMaximumDelay = Duration.ofMinutes(15);

    @NotNull
    @DurationMin(seconds = 1)
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration smtpConnectionTimeout = Duration.ofSeconds(5);

    @NotNull
    @DurationMin(seconds = 1)
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration smtpReadTimeout = Duration.ofSeconds(10);

    @NotNull
    @DurationMin(seconds = 1)
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration smtpWriteTimeout = Duration.ofSeconds(10);

    @jakarta.validation.constraints.AssertTrue(
            message = "retry maximum delay must not be shorter than retry initial delay"
    )
    public boolean isRetryMaximumDelayAtLeastInitialDelay() {
        return retryMaximumDelay == null
                || retryInitialDelay == null
                || !retryMaximumDelay.minus(retryInitialDelay).isNegative();
    }

    @jakarta.validation.constraints.AssertTrue(
            message = "combined SMTP timeout budget must be shorter than the stale claim lease"
    )
    public boolean isDeliveryTimeoutsShorterThanStaleClaim() {
        return staleClaimAfter == null
                || smtpConnectionTimeout == null
                || smtpReadTimeout == null
                || smtpWriteTimeout == null
                || smtpConnectionTimeout.plus(smtpReadTimeout).plus(smtpWriteTimeout)
                .compareTo(staleClaimAfter) < 0;
    }

    @jakarta.validation.constraints.AssertTrue(message = "link base URL must be absolute and HTTPS when secure links are required")
    public boolean isLinkBaseUrlAllowed() {
        if (!requireSecureLinks) {
            return true;
        }
        try {
            URI uri = URI.create(linkBaseUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getAuthority() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
