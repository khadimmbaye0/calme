package com.calme.calendar;

import java.time.Instant;

import com.calme.tasks.Task;
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
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "task_id")
    private Long taskId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventKind kind;

    @Column(name = "recurrence_rule")
    private String recurrenceRule;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false)
    private EventCreator createdBy;

    protected Event() {
    }

    public Event(AppUser user, String title, Instant startAt, Instant endAt, EventKind kind, EventCreator createdBy) {
        this.user = user;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.kind = kind;
        this.createdBy = createdBy;
    }

    public Event(
            AppUser user,
            Task task,
            String title,
            Instant startAt,
            Instant endAt,
            EventKind kind,
            EventCreator createdBy) {
        this(user, title, startAt, endAt, kind, createdBy);
        this.taskId = task.getId();
    }

    public void update(String title, Instant startAt, Instant endAt, EventKind kind) {
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.kind = kind;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public EventKind getKind() {
        return kind;
    }

    public String getRecurrenceRule() {
        return recurrenceRule;
    }

    public EventCreator getCreatedBy() {
        return createdBy;
    }
}
