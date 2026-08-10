package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
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

    List<Task> findByStatus(Status status);

    List<Task> findByPriority(Priority priority);

    List<Task> findByProjectIdAndStatus(Long projectId, Status status);

    List<Task> findByDueDateBeforeAndStatusNot(LocalDate date, Status status);
    
    List<Task> findByTitleContainingIgnoreCase(String title);

    List<Task> findByGenerationRunIdOrderByPositionAsc(UUID generationRunId);

    long countByProjectId(Long projectId);

    @Query("""
            select task.project.id as projectId, count(task.id) as taskCount
            from Task task
            group by task.project.id
            """)
    List<ProjectTaskCount> countTasksByProject();
}
