package com.calme.tasks;

import java.time.Instant;
import java.time.LocalDate;

import com.calme.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "task")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "project_id")
    private Long projectId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "estimate_min", nullable = false)
    private int estimateMin;

    @Column(name = "estimate_max", nullable = false)
    private int estimateMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Task() {
    }

    public Task(
            AppUser user,
            String title,
            int estimateMin,
            int estimateMax,
            TaskPriority priority,
            LocalDate dueDate) {
        this.user = user;
        this.title = title;
        this.estimateMin = estimateMin;
        this.estimateMax = estimateMax;
        this.priority = priority;
        this.status = TaskStatus.TODO;
        this.dueDate = dueDate;
        this.createdAt = Instant.now();
    }

    public void start() {
        this.status = TaskStatus.DOING;
    }

    public void done() {
        this.status = TaskStatus.DONE;
    }

    public void deferTo(LocalDate deferDate) {
        this.status = TaskStatus.TODO;
        this.dueDate = deferDate;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public int getEstimateMin() {
        return estimateMin;
    }

    public int getEstimateMax() {
        return estimateMax;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
