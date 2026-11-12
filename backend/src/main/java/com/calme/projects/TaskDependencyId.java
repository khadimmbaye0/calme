package com.calme.projects;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class TaskDependencyId implements Serializable {

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "depends_on_task_id")
    private Long dependsOnTaskId;

    protected TaskDependencyId() {
    }

    public TaskDependencyId(Long taskId, Long dependsOnTaskId) {
        this.taskId = taskId;
        this.dependsOnTaskId = dependsOnTaskId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getDependsOnTaskId() {
        return dependsOnTaskId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskDependencyId that)) {
            return false;
        }
        return Objects.equals(taskId, that.taskId)
                && Objects.equals(dependsOnTaskId, that.dependsOnTaskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, dependsOnTaskId);
    }
}
