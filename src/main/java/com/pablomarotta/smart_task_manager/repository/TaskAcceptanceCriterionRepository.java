package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.TaskAcceptanceCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAcceptanceCriterionRepository extends JpaRepository<TaskAcceptanceCriterion, Long> {
}
