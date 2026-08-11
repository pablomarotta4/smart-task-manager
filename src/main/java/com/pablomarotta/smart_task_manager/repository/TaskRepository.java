package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    interface ProjectTaskCount {
        Long getProjectId();

        Long getTaskCount();
    }

    List<Task> findByProjectId(Long projectId);

    List<Task> findByProjectIdOrderByPositionAsc(Long projectId);

    List<Task> findByAssigneeId(Long assigneeId);

    List<Task> findByAssigneeIdAndProjectOwnerUsername(Long assigneeId, String username);

    List<Task> findByAssigneeUsernameOrderByDueDateAscPositionAsc(String username);

    List<Task> findByStatus(Status status);

    List<Task> findByStatusAndProjectOwnerUsername(Status status, String username);

    List<Task> findByProjectOwnerUsername(String username);

    Optional<Task> findByIdAndProjectOwnerUsername(Long id, String username);

    Optional<Task> findByIdAndAssigneeUsername(Long id, String username);

    List<Task> findByPriority(Priority priority);

    List<Task> findByProjectIdAndStatus(Long projectId, Status status);

    List<Task> findByDueDateBeforeAndStatusNot(LocalDate date, Status status);
    
    List<Task> findByTitleContainingIgnoreCase(String title);

    List<Task> findByGenerationRunIdOrderByPositionAsc(UUID generationRunId);

    long countByProjectId(Long projectId);

    @Modifying
    @Query("""
            update Task task
            set task.assignee = null
            where task.project.id = :projectId and task.assignee.id = :userId
            """)
    int clearAssigneeForProjectAndUser(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );

    @Query("""
            select task.project.id as projectId, count(task.id) as taskCount
            from Task task
            group by task.project.id
            """)
    List<ProjectTaskCount> countTasksByProject();

    @Query("""
            select task.project.id as projectId, count(task.id) as taskCount
            from Task task
            where task.project.owner.username = :username
            group by task.project.id
            """)
    List<ProjectTaskCount> countTasksByProjectOwnerUsername(@Param("username") String username);
}
