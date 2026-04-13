package ru.vstu.medsim.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_inventory_items")
public class TeamInventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private SessionTeam team;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TeamInventoryItem() {
    }

    public TeamInventoryItem(SessionTeam team, String itemName, Integer quantity) {
        this.team = team;
        this.itemName = itemName;
        this.quantity = quantity;
    }

    public void consume(int quantity) {
        this.quantity = this.quantity - quantity;
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

    public String getItemName() {
        return itemName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
