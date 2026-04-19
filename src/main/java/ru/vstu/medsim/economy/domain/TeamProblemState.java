package ru.vstu.medsim.economy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_problem_states")
public class TeamProblemState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_room_state_id", nullable = false)
    private TeamRoomState teamRoomState;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_room_problem_template_id", nullable = false)
    private ClinicRoomProblemTemplate problemTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SessionProblemStatus status;

    @Column(name = "stage_number", nullable = false)
    private Integer stageNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected TeamProblemState() {
    }

    public TeamProblemState(TeamRoomState teamRoomState, ClinicRoomProblemTemplate problemTemplate, SessionProblemStatus status) {
        this(teamRoomState, problemTemplate, status, problemTemplate.getStageNumber());
    }

    public TeamProblemState(
            TeamRoomState teamRoomState,
            ClinicRoomProblemTemplate problemTemplate,
            SessionProblemStatus status,
            Integer stageNumber
    ) {
        this.teamRoomState = teamRoomState;
        this.problemTemplate = problemTemplate;
        this.status = status;
        this.stageNumber = stageNumber;
    }

    public void updateStatus(SessionProblemStatus status) {
        this.status = status;
        this.resolvedAt = status == SessionProblemStatus.RESOLVED
                ? LocalDateTime.now()
                : null;
    }

    public void assignStageNumber(Integer stageNumber) {
        this.stageNumber = stageNumber;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public TeamRoomState getTeamRoomState() {
        return teamRoomState;
    }

    public ClinicRoomProblemTemplate getProblemTemplate() {
        return problemTemplate;
    }

    public SessionProblemStatus getStatus() {
        return status;
    }

    public Integer getStageNumber() {
        return stageNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
}
