package com.pablomarotta.smart_task_manager.repository;

import com.pablomarotta.smart_task_manager.model.Priority;
import com.pablomarotta.smart_task_manager.model.Status;
import com.pablomarotta.smart_task_manager.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectId(Long projectId);

    List<Task> findByAssigneeId(Long assigneeId);

    List<Task> findByStatus(Status status);

    List<Task> findByPriority(Priority priority);

    List<Task> findByProjectIdAndStatus(Long projectId, Status status);

    List<Task> findByDueDateBeforeAndStatusNot(LocalDate date, Status status);
    
    List<Task> findByTitleContainingIgnoreCase(String title);
}
