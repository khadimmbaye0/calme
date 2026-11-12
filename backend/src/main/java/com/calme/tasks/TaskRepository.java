package com.calme.tasks;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserIdAndStatusInOrderByDueDateAscPriorityAscEstimateMaxAsc(
            Long userId, Collection<TaskStatus> statuses);

    List<Task> findByUserIdAndStatusAndDueDateLessThanEqualOrderByPriorityAscDueDateAscEstimateMaxAsc(
            Long userId, TaskStatus status, LocalDate dueDate);

    List<Task> findByUserIdAndStatusAndDueDateLessThanEqual(
            Long userId, TaskStatus status, LocalDate dueDate);

    Optional<Task> findByIdAndUserId(Long id, Long userId);
}
