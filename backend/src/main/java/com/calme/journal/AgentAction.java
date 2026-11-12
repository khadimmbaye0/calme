package com.calme.journal;

import java.time.Instant;

import com.calme.user.AppUser;
import com.fasterxml.jackson.databind.JsonNode;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "agent_action")
public class AgentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private String intent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false)
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inverse_payload_json")
    private JsonNode inversePayload;

    @Column(nullable = false)
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentActionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentAction() {
    }

    public AgentAction(
            AppUser user,
            String intent,
            JsonNode payload,
            JsonNode inversePayload,
            String explanation) {
        this.user = user;
        this.intent = intent;
        this.payload = payload;
        this.inversePayload = inversePayload;
        this.explanation = explanation;
        this.status = AgentActionStatus.APPLIED;
        this.createdAt = Instant.now();
    }

    public void markUndone() {
        this.status = AgentActionStatus.UNDONE;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getIntent() {
        return intent;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public JsonNode getInversePayload() {
        return inversePayload;
    }

    public String getExplanation() {
        return explanation;
    }

    public AgentActionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
