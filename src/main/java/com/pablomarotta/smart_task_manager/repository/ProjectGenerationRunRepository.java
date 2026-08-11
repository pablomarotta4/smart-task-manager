package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.ProjectGenerationRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ProjectGenerationRunRepository extends JpaRepository<ProjectGenerationRun, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from ProjectGenerationRun run where run.id = :id")
    Optional<ProjectGenerationRun> findLockedById(@Param("id") UUID id);

    List<ProjectGenerationRun> findTop10ByRequestedByUsernameOrderByUpdatedAtDesc(String username);

    Optional<ProjectGenerationRun> findByIdAndRequestedByUsername(UUID id, String username);
}
