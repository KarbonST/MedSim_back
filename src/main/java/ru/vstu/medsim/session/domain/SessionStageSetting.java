package ru.vstu.medsim.session.domain;

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
import jakarta.persistence.UniqueConstraint;
import ru.vstu.medsim.player.domain.GameSession;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "session_stage_settings",
        uniqueConstraints = @UniqueConstraint(name = "uq_session_stage_number", columnNames = {"game_session_id", "stage_number"})
)
public class SessionStageSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_session_id", nullable = false)
    private GameSession gameSession;

    @Column(name = "stage_number", nullable = false)
    private Integer stageNumber;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_mode", nullable = false, length = 50)
    private StageInteractionMode interactionMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SessionStageSetting() {
    }

    public SessionStageSetting(
            GameSession gameSession,
            Integer stageNumber,
            Integer durationMinutes,
            StageInteractionMode interactionMode
    ) {
        this.gameSession = gameSession;
        this.stageNumber = stageNumber;
        this.durationMinutes = durationMinutes;
        this.interactionMode = interactionMode;
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

    public GameSession getGameSession() {
        return gameSession;
    }

    public Integer getStageNumber() {
        return stageNumber;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public StageInteractionMode getInteractionMode() {
        return interactionMode;
    }
}
