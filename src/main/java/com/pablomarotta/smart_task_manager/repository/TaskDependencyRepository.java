package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {

    @Query("""
            select dependency
            from TaskDependency dependency
            join fetch dependency.task task
            join fetch dependency.dependsOnTask dependsOnTask
            where task.project.id = :projectId
            order by task.position, dependsOnTask.position
            """)
    List<TaskDependency> findByProjectId(@Param("projectId") Long projectId);

    @Query("""
            select dependency
            from TaskDependency dependency
            join fetch dependency.task task
            join fetch dependency.dependsOnTask dependsOnTask
            where task.assignee.username = :username
            order by task.dueDate, task.position, dependsOnTask.position
            """)
    List<TaskDependency> findByTaskAssigneeUsername(@Param("username") String username);
}
