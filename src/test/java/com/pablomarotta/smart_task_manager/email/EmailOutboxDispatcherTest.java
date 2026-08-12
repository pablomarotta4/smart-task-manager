package com.pablomarotta.smart_task_manager.email;

import com.pablomarotta.smart_task_manager.config.EmailOutboxProperties;
import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;
import com.pablomarotta.smart_task_manager.service.EmailOutboxClaimService;
import com.pablomarotta.smart_task_manager.service.EmailOutboxDeliveryResult;
import com.pablomarotta.smart_task_manager.service.EmailOutboxDeliveryService;
import com.pablomarotta.smart_task_manager.service.OutboxClaim;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailOutboxDispatcherTest {

    @Mock
    private EmailOutboxClaimService claimService;

    @Mock
    private EmailOutboxDeliveryService deliveryService;

    @Test
    void delegatesEachClaimToTheTransactionalDeliveryFence() {
        EmailOutboxProperties properties = new EmailOutboxProperties();
        EmailOutboxDispatcher dispatcher = new EmailOutboxDispatcher(claimService, deliveryService, properties);
        OutboxClaim claim = new OutboxClaim(UUID.randomUUID(), LocalDateTime.now(), 1);
        when(claimService.claimDue()).thenReturn(List.of(claim));
        when(deliveryService.deliver(claim)).thenReturn(new EmailOutboxDeliveryResult(
                claim.outboxId(), UUID.randomUUID(), AccountActionPurpose.VERIFY_EMAIL,
                claim.attempt(), EmailOutboxDeliveryResult.Status.SENT, null, true
        ));

        dispatcher.dispatchDueEmails();

        verify(deliveryService).deliver(claim);
    }

    @Test
    void doesNotClaimOrDeliverWhenDisabled() {
        EmailOutboxProperties properties = new EmailOutboxProperties();
        properties.setEnabled(false);
        EmailOutboxDispatcher dispatcher = new EmailOutboxDispatcher(claimService, deliveryService, properties);

        dispatcher.dispatchDueEmails();

        verify(claimService, never()).claimDue();
        verify(deliveryService, never()).deliver(org.mockito.ArgumentMatchers.any());
    }
}
