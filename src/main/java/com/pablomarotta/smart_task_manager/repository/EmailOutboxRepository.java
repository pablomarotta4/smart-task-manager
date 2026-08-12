package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.EmailOutbox;
import com.pablomarotta.smart_task_manager.model.EmailOutboxState;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    interface DeliveryClaimIdentity {

        Long getUserId();

        UUID getActionId();
    }

    Optional<EmailOutbox> findByAccountActionRequest_Id(UUID accountActionRequestId);

    @Query(value = """
            select *
            from email_outbox
            where (state = 'PENDING' and available_at <= :now)
               or (state = 'PROCESSING' and claimed_at <= :staleBefore)
            order by available_at asc, id asc
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<EmailOutbox> findClaimableForUpdate(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("batchSize") int batchSize
    );

    @Query("""
            select outbox.recipient.id as userId, outbox.accountActionRequest.id as actionId
            from EmailOutbox outbox
            where outbox.id = :outboxId
              and outbox.state = :state
              and outbox.claimedAt = :claimedAt
              and outbox.attempts = :attempt
            """)
    Optional<DeliveryClaimIdentity> findDeliveryClaimIdentity(
            @Param("outboxId") UUID outboxId,
            @Param("state") EmailOutboxState state,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("attempt") int attempt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select outbox
            from EmailOutbox outbox
            where outbox.id = :outboxId
              and outbox.state = :state
              and outbox.claimedAt = :claimedAt
              and outbox.attempts = :attempt
            """)
    Optional<EmailOutbox> findCurrentClaimForUpdate(
            @Param("outboxId") UUID outboxId,
            @Param("state") EmailOutboxState state,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("attempt") int attempt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update email_outbox
            set state = 'SENT', sent_at = :sentAt, last_error_code = null, updated_at = :updatedAt
            where id = :outboxId
              and state = 'PROCESSING'
              and claimed_at = :claimedAt
              and attempts = :attempt
            """, nativeQuery = true)
    int markSent(
            @Param("outboxId") UUID outboxId,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("attempt") int attempt,
            @Param("sentAt") LocalDateTime sentAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update email_outbox
            set state = 'PENDING', available_at = :availableAt, claimed_at = null, sent_at = null,
                last_error_code = null, updated_at = :updatedAt
            where id = :outboxId
              and state = 'PROCESSING'
              and claimed_at = :claimedAt
              and attempts = :attempt
            """, nativeQuery = true)
    int releaseForRetry(
            @Param("outboxId") UUID outboxId,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("attempt") int attempt,
            @Param("availableAt") LocalDateTime availableAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update email_outbox
            set state = 'DEAD', sent_at = null, last_error_code = :failureCode, updated_at = :updatedAt
            where id = :outboxId
              and state = 'PROCESSING'
              and claimed_at = :claimedAt
              and attempts = :attempt
            """, nativeQuery = true)
    int markDead(
            @Param("outboxId") UUID outboxId,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("attempt") int attempt,
            @Param("failureCode") String failureCode,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
