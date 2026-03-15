package ru.vstu.medsim.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.session.domain.SessionTeam;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_chat_messages")
public class TeamChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_session_id", nullable = false)
    private GameSession gameSession;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private SessionTeam team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private SessionParticipant participant;

    @Column(name = "message_text", nullable = false, length = 1000)
    private String messageText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TeamChatMessage() {
    }

    public TeamChatMessage(GameSession gameSession, SessionTeam team, SessionParticipant participant, String messageText) {
        this.gameSession = gameSession;
        this.team = team;
        this.participant = participant;
        this.messageText = messageText;
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

    public SessionTeam getTeam() {
        return team;
    }

    public SessionParticipant getParticipant() {
        return participant;
    }

    public String getMessageText() {
        return messageText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
