package ru.vstu.medsim.economy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import ru.vstu.medsim.session.domain.SessionTeam;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "team_economy_states")
public class TeamEconomyState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false, unique = true)
    private SessionTeam team;

    @Column(name = "current_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "current_stage_time_units", nullable = false)
    private Integer currentStageTimeUnits;

    @Column(name = "total_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalIncome;

    @Column(name = "total_expenses", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalExpenses;

    @Column(name = "total_penalties", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPenalties;

    @Column(name = "total_bonuses", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalBonuses;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TeamEconomyState() {
    }

    public TeamEconomyState(SessionTeam team, BigDecimal currentBalance, Integer currentStageTimeUnits) {
        this.team = team;
        this.currentBalance = currentBalance;
        this.currentStageTimeUnits = currentStageTimeUnits;
        this.totalIncome = BigDecimal.ZERO.setScale(2);
        this.totalExpenses = BigDecimal.ZERO.setScale(2);
        this.totalPenalties = BigDecimal.ZERO.setScale(2);
        this.totalBonuses = BigDecimal.ZERO.setScale(2);
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (totalIncome == null) {
            totalIncome = BigDecimal.ZERO.setScale(2);
        }
        if (totalExpenses == null) {
            totalExpenses = BigDecimal.ZERO.setScale(2);
        }
        if (totalPenalties == null) {
            totalPenalties = BigDecimal.ZERO.setScale(2);
        }
        if (totalBonuses == null) {
            totalBonuses = BigDecimal.ZERO.setScale(2);
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void resetStageTime(int stageTimeUnits) {
        this.currentStageTimeUnits = stageTimeUnits;
    }

    public void resetForLobby(BigDecimal startingBudget, int stageTimeUnits) {
        this.currentBalance = startingBudget;
        this.currentStageTimeUnits = stageTimeUnits;
        this.totalIncome = BigDecimal.ZERO.setScale(2);
        this.totalExpenses = BigDecimal.ZERO.setScale(2);
        this.totalPenalties = BigDecimal.ZERO.setScale(2);
        this.totalBonuses = BigDecimal.ZERO.setScale(2);
    }

    public Long getId() {
        return id;
    }

    public SessionTeam getTeam() {
        return team;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public Integer getCurrentStageTimeUnits() {
        return currentStageTimeUnits;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public BigDecimal getTotalPenalties() {
        return totalPenalties;
    }

    public BigDecimal getTotalBonuses() {
        return totalBonuses;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
