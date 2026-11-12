package com.calme.projects;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, TaskDependencyId> {

    @Query("select dependency from TaskDependency dependency where dependency.task.id in :taskIds")
    List<TaskDependency> findByTaskIdIn(@Param("taskIds") Collection<Long> taskIds);
}
