package ru.vstu.medsim.economy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import ru.vstu.medsim.session.domain.SessionTeam;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_room_states")
public class TeamRoomState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private SessionTeam team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_room_template_id", nullable = false)
    private ClinicRoomTemplate clinicRoom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TeamRoomState() {
    }

    public TeamRoomState(SessionTeam team, ClinicRoomTemplate clinicRoom) {
        this.team = team;
        this.clinicRoom = clinicRoom;
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

    public SessionTeam getTeam() {
        return team;
    }

    public ClinicRoomTemplate getClinicRoom() {
        return clinicRoom;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
