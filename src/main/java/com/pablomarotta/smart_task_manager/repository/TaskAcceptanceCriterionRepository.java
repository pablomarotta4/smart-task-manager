package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.TaskAcceptanceCriterion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskAcceptanceCriterionRepository extends JpaRepository<TaskAcceptanceCriterion, Long> {

    @Query("""
            select criterion
            from TaskAcceptanceCriterion criterion
            join fetch criterion.task task
            where task.project.id = :projectId
            order by task.position, criterion.position
            """)
    List<TaskAcceptanceCriterion> findByProjectId(@Param("projectId") Long projectId);

    @Query("""
            select criterion
            from TaskAcceptanceCriterion criterion
            join fetch criterion.task task
            where task.assignee.username = :username
              and exists (
                  select membership.id from ProjectMembership membership
                  where membership.project = task.project
                    and membership.user.username = :username
              )
            order by task.dueDate, task.position, criterion.position
            """)
    List<TaskAcceptanceCriterion> findByTaskAssigneeUsername(@Param("username") String username);
}
