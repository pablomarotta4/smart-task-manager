package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerId(Long ownerId);

    List<Project> findByNameContainingIgnoreCase(String name);

    boolean existsByNameAndOwnerId(String name, Long ownerId);
}
