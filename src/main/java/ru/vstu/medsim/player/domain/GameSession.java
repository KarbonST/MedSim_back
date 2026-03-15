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

    protected GameSession() {
    }

    public GameSession(String code, String name, GameSessionStatus status) {
        this.code = code;
        this.name = name;
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
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

        status = GameSessionStatus.PAUSED;
    }

    public void finish() {
        if (status == GameSessionStatus.LOBBY) {
            throw new IllegalStateException("Нельзя завершить ещё не начатую сессию.");
        }

        if (status == GameSessionStatus.FINISHED) {
            throw new IllegalStateException("Сессия уже завершена.");
        }

        status = GameSessionStatus.FINISHED;
        finishedAt = LocalDateTime.now();
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
}
