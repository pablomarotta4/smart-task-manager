package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    @Query("select token.user.id from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken token
            set token.revokedAt = :revokedAt
            where token.user.id = :userId
              and token.familyId = :familyId
              and token.revokedAt is null
            """)
    int revokeFamilyByUserIdAndFamilyId(
            @Param("userId") Long userId,
            @Param("familyId") UUID familyId,
            @Param("revokedAt") LocalDateTime revokedAt
    );

    @Modifying
    @Transactional
    @Query("delete from RefreshToken token where token.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
