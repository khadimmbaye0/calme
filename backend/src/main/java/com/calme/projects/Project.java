package com.calme.projects;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.calme.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String goal;

    @Column(nullable = false)
    private LocalDate deadline;

    @Column(nullable = false)
    private String status;

    @Column(name = "buffer_ratio", nullable = false)
    private BigDecimal bufferRatio;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Project() {
    }

    public Project(AppUser user, String title, String goal, LocalDate deadline, BigDecimal bufferRatio) {
        this.user = user;
        this.title = title;
        this.goal = goal;
        this.deadline = deadline;
        this.status = "ACTIVE";
        this.bufferRatio = bufferRatio;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public String getGoal() {
        return goal;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getBufferRatio() {
        return bufferRatio;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
