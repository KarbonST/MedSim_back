package ru.vstu.medsim.player.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import ru.vstu.medsim.session.domain.SessionTeam;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "session_participants",
        uniqueConstraints = @UniqueConstraint(name = "uq_session_player", columnNames = {"game_session_id", "player_id"})
)
public class SessionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_session_id", nullable = false)
    private GameSession gameSession;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne
    @JoinColumn(name = "team_id")
    private SessionTeam team;

    @Column(name = "game_role", length = 100)
    private String gameRole;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    protected SessionParticipant() {
    }

    public SessionParticipant(GameSession gameSession, Player player) {
        this.gameSession = gameSession;
        this.player = player;
    }

    @PrePersist
    void prePersist() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }

    public void assignTeam(SessionTeam team) {
        this.team = team;
    }

    public void assignGameRole(String gameRole) {
        this.gameRole = gameRole;
    }

    public Long getId() {
        return id;
    }

    public GameSession getGameSession() {
        return gameSession;
    }

    public Player getPlayer() {
        return player;
    }

    public SessionTeam getTeam() {
        return team;
    }

    public String getGameRole() {
        return gameRole;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
