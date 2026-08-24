package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.AccountActionPurpose;
import com.pablomarotta.smart_task_manager.model.AccountActionRequest;
import com.pablomarotta.smart_task_manager.model.AccountActionState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountActionRequestRepository extends JpaRepository<AccountActionRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select action
            from AccountActionRequest action
            where action.id = :id and action.tokenHash = :tokenHash
            """)
    Optional<AccountActionRequest> findForUpdateByIdAndTokenHash(
            @Param("id") UUID id,
            @Param("tokenHash") String tokenHash
    );

    @Query("""
            select action.user.id
            from AccountActionRequest action
            where action.id = :id and action.tokenHash = :tokenHash
            """)
    Optional<Long> findUserIdByIdAndTokenHash(
            @Param("id") UUID id,
            @Param("tokenHash") String tokenHash
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AccountActionRequest action
            set action.state = :invalidatedState,
                action.invalidatedAt = :invalidatedAt,
                action.updatedAt = :invalidatedAt
            where action.user.id = :userId
              and action.purpose = :purpose
              and action.state = :pendingState
            """)
    int updateStateForUserPurpose(
            @Param("userId") Long userId,
            @Param("purpose") AccountActionPurpose purpose,
            @Param("pendingState") AccountActionState pendingState,
            @Param("invalidatedState") AccountActionState invalidatedState,
            @Param("invalidatedAt") LocalDateTime invalidatedAt
    );

    default int invalidatePendingByUserIdAndPurpose(
            Long userId,
            AccountActionPurpose purpose,
            LocalDateTime invalidatedAt
    ) {
        return updateStateForUserPurpose(
                userId,
                purpose,
                AccountActionState.PENDING,
                AccountActionState.INVALIDATED,
                invalidatedAt
        );
    }
}
