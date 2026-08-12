package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.ProjectMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, Long> {

    @EntityGraph(attributePaths = "user")
    List<ProjectMembership> findByProjectIdOrderByJoinedAtAsc(Long projectId);

    Optional<ProjectMembership> findByProjectIdAndUserId(Long projectId, Long userId);

    Optional<ProjectMembership> findByProjectIdAndUserUsername(Long projectId, String username);

    @EntityGraph(attributePaths = {"project", "project.owner"})
    List<ProjectMembership> findByUserUsernameOrderByProjectCreatedAtDesc(String username);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);
}
