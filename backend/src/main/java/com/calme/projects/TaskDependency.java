package com.calme.projects;

import com.calme.tasks.Task;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "task_dependency")
public class TaskDependency {

    @EmbeddedId
    private TaskDependencyId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("taskId")
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("dependsOnTaskId")
    @JoinColumn(name = "depends_on_task_id", nullable = false)
    private Task dependsOnTask;

    protected TaskDependency() {
    }

    public TaskDependency(Task task, Task dependsOnTask) {
        this.task = task;
        this.dependsOnTask = dependsOnTask;
        this.id = new TaskDependencyId(task.getId(), dependsOnTask.getId());
    }

    public TaskDependencyId getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public Task getDependsOnTask() {
        return dependsOnTask;
    }
}
