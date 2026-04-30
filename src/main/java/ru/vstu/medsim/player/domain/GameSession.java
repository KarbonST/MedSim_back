package ru.vstu.medsim.player.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import ru.vstu.medsim.economy.domain.FinalStageCrisisType;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_sessions")
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private GameSessionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "active_stage_number")
    private Integer activeStageNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "timer_status", nullable = false, length = 30)
    private SessionTimerStatus timerStatus;

    @Column(name = "timer_remaining_seconds")
    private Integer timerRemainingSeconds;

    @Column(name = "timer_updated_at")
    private LocalDateTime timerUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_stage_crisis_type", length = 40)
    private FinalStageCrisisType finalStageCrisisType;

    @Column(name = "final_stage_crisis_activated_at")
    private LocalDateTime finalStageCrisisActivatedAt;

    protected GameSession() {
    }

    public GameSession(String code, String name, GameSessionStatus status) {
        this.code = code;
        this.name = name;
        this.status = status;
        this.timerStatus = SessionTimerStatus.STOPPED;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (timerStatus == null) {
            timerStatus = SessionTimerStatus.STOPPED;
        }
    }

    public void start() {
        if (status == GameSessionStatus.FINISHED) {
            throw new IllegalStateException("Нельзя запустить завершённую сессию.");
        }

        if (status == GameSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Сессия уже запущена.");
        }

        status = GameSessionStatus.IN_PROGRESS;

        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    public void pause() {
        if (status == GameSessionStatus.LOBBY) {
            throw new IllegalStateException("Нельзя поставить на паузу ещё не начатую сессию.");
        }

        if (status == GameSessionStatus.PAUSED) {
            throw new IllegalStateException("Сессия уже находится на паузе.");
        }

        if (status == GameSessionStatus.FINISHED) {
            throw new IllegalStateException("Нельзя поставить на паузу завершённую сессию.");
        }

        if (timerStatus == SessionTimerStatus.RUNNING) {
            pauseTimer();
        }

        status = GameSessionStatus.PAUSED;
    }

    public void finish() {
        if (status == GameSessionStatus.LOBBY) {
            throw new IllegalStateException("Нельзя завершить ещё не начатую сессию.");
        }

        if (status == GameSessionStatus.FINISHED) {
            throw new IllegalStateException("Сессия уже завершена.");
        }

        if (timerStatus == SessionTimerStatus.RUNNING) {
            timerRemainingSeconds = getRemainingSecondsAt(LocalDateTime.now());
        }

        timerStatus = SessionTimerStatus.STOPPED;
        timerUpdatedAt = null;
        status = GameSessionStatus.FINISHED;
        finishedAt = LocalDateTime.now();
    }

    public void initializeStageRuntime(int stageNumber, int durationMinutes) {
        activeStageNumber = stageNumber;
        timerRemainingSeconds = durationMinutes * 60;
        timerStatus = SessionTimerStatus.STOPPED;
        timerUpdatedAt = null;
    }

    public void selectStage(int stageNumber, int durationMinutes) {
        if (status == GameSessionStatus.FINISHED) {
            throw new IllegalStateException("Нельзя менять этап в завершённой сессии.");
        }

        initializeStageRuntime(stageNumber, durationMinutes);
    }

    public void startTimer() {
        if (status == GameSessionStatus.FINISHED) {
            throw new IllegalStateException("Нельзя запускать таймер в завершённой сессии.");
        }

        if (activeStageNumber == null || timerRemainingSeconds == null) {
            throw new IllegalStateException("Сначала выберите этап с сохранённой длительностью.");
        }

        if (timerRemainingSeconds <= 0) {
            throw new IllegalStateException("Для запуска таймера сначала сбросьте время текущего этапа.");
        }

        if (timerStatus == SessionTimerStatus.RUNNING) {
            throw new IllegalStateException("Таймер текущего этапа уже запущен.");
        }

        timerStatus = SessionTimerStatus.RUNNING;
        timerUpdatedAt = LocalDateTime.now();
    }

    public void pauseTimer() {
        if (timerStatus != SessionTimerStatus.RUNNING) {
            throw new IllegalStateException("Таймер сейчас не запущен.");
        }

        timerRemainingSeconds = getRemainingSecondsAt(LocalDateTime.now());
        timerStatus = SessionTimerStatus.PAUSED;
        timerUpdatedAt = LocalDateTime.now();
    }

    public void resetTimer(int durationMinutes) {
        if (activeStageNumber == null) {
            throw new IllegalStateException("Сначала выберите этап.");
        }

        timerRemainingSeconds = durationMinutes * 60;
        timerStatus = SessionTimerStatus.STOPPED;
        timerUpdatedAt = null;
    }

    public void restartToLobby(int stageNumber, int durationMinutes) {
        status = GameSessionStatus.LOBBY;
        startedAt = null;
        finishedAt = null;
        finalStageCrisisType = null;
        finalStageCrisisActivatedAt = null;
        initializeStageRuntime(stageNumber, durationMinutes);
    }

    public void activateFinalStageCrisis(FinalStageCrisisType crisisType) {
        if (crisisType == null || finalStageCrisisType != null) {
            return;
        }

        finalStageCrisisType = crisisType;
        finalStageCrisisActivatedAt = LocalDateTime.now();
    }

    public int getRemainingSecondsAt(LocalDateTime moment) {
        if (timerRemainingSeconds == null) {
            return 0;
        }

        if (timerStatus != SessionTimerStatus.RUNNING || timerUpdatedAt == null) {
            return Math.max(timerRemainingSeconds, 0);
        }

        long elapsedSeconds = Math.max(Duration.between(timerUpdatedAt, moment).getSeconds(), 0);
        return Math.max((int) (timerRemainingSeconds - elapsedSeconds), 0);
    }

    public LocalDateTime getTimerEndsAt() {
        if (timerStatus != SessionTimerStatus.RUNNING || timerUpdatedAt == null || timerRemainingSeconds == null) {
            return null;
        }

        return timerUpdatedAt.plusSeconds(timerRemainingSeconds);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void rename(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public GameSessionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public Integer getActiveStageNumber() {
        return activeStageNumber;
    }

    public SessionTimerStatus getTimerStatus() {
        return timerStatus;
    }

    public Integer getTimerRemainingSeconds() {
        return timerRemainingSeconds;
    }

    public LocalDateTime getTimerUpdatedAt() {
        return timerUpdatedAt;
    }

    public FinalStageCrisisType getFinalStageCrisisType() {
        return finalStageCrisisType;
    }

    public LocalDateTime getFinalStageCrisisActivatedAt() {
        return finalStageCrisisActivatedAt;
    }
}
