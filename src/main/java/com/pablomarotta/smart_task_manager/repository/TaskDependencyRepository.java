package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {
}
