package com.pablomarotta.smart_task_manager.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailOutbox implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_action_request_id", nullable = false, unique = true)
    private AccountActionRequest accountActionRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmailOutboxKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountActionPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailOutboxState state;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Override
    @Transient
    public boolean isNew() {
        return createdAt == null;
    }
}
