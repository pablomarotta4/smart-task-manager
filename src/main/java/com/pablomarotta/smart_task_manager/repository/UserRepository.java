package com.pablomarotta.smart_task_manager.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.pablomarotta.smart_task_manager.model.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.username = :username and user.active = true")
    Optional<User> findActiveForUpdateByUsername(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId and user.active = true")
    Optional<User> findActiveForUpdateById(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.emailNormalized = :emailNormalized and user.active = true")
    Optional<User> findActiveForUpdateByEmailNormalized(@Param("emailNormalized") String emailNormalized);
    
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailNormalized(String emailNormalized);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);

    boolean existsByEmailNormalized(String emailNormalized);

    boolean existsById(Long id);
}
