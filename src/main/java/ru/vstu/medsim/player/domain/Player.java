package ru.vstu.medsim.player.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "players",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uq_players_display_name_position",
                columnNames = {"display_name", "hospital_position"}
        )
)
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "hospital_position", nullable = false, length = 150)
    private String hospitalPosition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Player() {
    }

    public Player(String displayName, String hospitalPosition) {
        this.displayName = displayName;
        this.hospitalPosition = hospitalPosition;
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

    public String getDisplayName() {
        return displayName;
    }

    public String getHospitalPosition() {
        return hospitalPosition;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
