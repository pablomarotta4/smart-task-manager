package com.pablomarotta.smart_task_manager.email;

import com.pablomarotta.smart_task_manager.config.EmailOutboxProperties;
import com.pablomarotta.smart_task_manager.service.EmailOutboxClaimService;
import com.pablomarotta.smart_task_manager.service.EmailOutboxDeliveryResult;
import com.pablomarotta.smart_task_manager.service.EmailOutboxDeliveryService;
import com.pablomarotta.smart_task_manager.service.OutboxClaim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailOutboxDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(EmailOutboxDispatcher.class);

    private final EmailOutboxClaimService claimService;
    private final EmailOutboxDeliveryService deliveryService;
    private final EmailOutboxProperties properties;

    public EmailOutboxDispatcher(
            EmailOutboxClaimService claimService,
            EmailOutboxDeliveryService deliveryService,
            EmailOutboxProperties properties
    ) {
        this.claimService = claimService;
        this.deliveryService = deliveryService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${email-outbox.dispatch-delay-ms:1000}")
    public void dispatchDueEmails() {
        if (!properties.isEnabled()) {
            return;
        }
        for (OutboxClaim claim : claimService.claimDue()) {
            dispatch(claim);
        }
    }

    private void dispatch(OutboxClaim claim) {
        logTransition(deliveryService.deliver(claim));
    }

    private void logTransition(EmailOutboxDeliveryResult result) {
        LOG.info("Email outbox {} action {} purpose {} attempt {} {} category {} transitioned={}",
                result.outboxId(), result.actionId(), result.purpose(), result.attempt(), result.status(),
                result.failureCode(), result.transitioned());
    }
}
