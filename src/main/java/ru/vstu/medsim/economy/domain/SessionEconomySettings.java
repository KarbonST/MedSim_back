package ru.vstu.medsim.economy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import ru.vstu.medsim.player.domain.GameSession;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "session_economy_settings")
public class SessionEconomySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "game_session_id", nullable = false, unique = true)
    private GameSession gameSession;

    @Column(name = "starting_budget", nullable = false, precision = 12, scale = 2)
    private BigDecimal startingBudget;

    @Column(name = "stage_time_units", nullable = false)
    private Integer stageTimeUnits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SessionEconomySettings() {
    }

    public SessionEconomySettings(GameSession gameSession, BigDecimal startingBudget, Integer stageTimeUnits) {
        this.gameSession = gameSession;
        this.startingBudget = startingBudget;
        this.stageTimeUnits = stageTimeUnits;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void update(BigDecimal startingBudget, Integer stageTimeUnits) {
        this.startingBudget = startingBudget;
        this.stageTimeUnits = stageTimeUnits;
    }

    public Long getId() {
        return id;
    }

    public GameSession getGameSession() {
        return gameSession;
    }

    public BigDecimal getStartingBudget() {
        return startingBudget;
    }

    public Integer getStageTimeUnits() {
        return stageTimeUnits;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
