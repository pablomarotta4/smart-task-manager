package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerId(Long ownerId);

    List<Project> findByOwnerUsernameOrderByCreatedAtDesc(String username);

    Optional<Project> findByIdAndOwnerUsername(Long id, String username);

    List<Project> findByNameContainingIgnoreCase(String name);

    boolean existsByNameAndOwnerId(String name, Long ownerId);
}
