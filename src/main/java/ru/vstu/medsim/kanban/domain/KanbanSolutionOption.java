package ru.vstu.medsim.kanban.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import ru.vstu.medsim.economy.domain.ClinicRoomProblemTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "kanban_solution_options")
public class KanbanSolutionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_template_id", nullable = false)
    private ClinicRoomProblemTemplate problemTemplate;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "budget_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal budgetCost;

    @Column(name = "time_cost", nullable = false)
    private Integer timeCost;

    @Column(name = "required_item_name", length = 200)
    private String requiredItemName;

    @Column(name = "required_item_quantity", nullable = false)
    private Integer requiredItemQuantity;

    @Column(name = "base_success_probability", nullable = false, precision = 5, scale = 2)
    private BigDecimal baseSuccessProbability;

    @Column(name = "nursing_success_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal nursingSuccessMultiplier;

    @Column(name = "engineering_success_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal engineeringSuccessMultiplier;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected KanbanSolutionOption() {
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (requiredItemQuantity == null) {
            requiredItemQuantity = 0;
        }
        if (sortOrder == null) {
            sortOrder = 1;
        }
        if (baseSuccessProbability == null) {
            baseSuccessProbability = BigDecimal.ONE.setScale(2);
        }
        if (nursingSuccessMultiplier == null) {
            nursingSuccessMultiplier = BigDecimal.ONE.setScale(2);
        }
        if (engineeringSuccessMultiplier == null) {
            engineeringSuccessMultiplier = BigDecimal.ONE.setScale(2);
        }
        if (active == null) {
            active = true;
        }
    }

    public Long getId() {
        return id;
    }

    public ClinicRoomProblemTemplate getProblemTemplate() {
        return problemTemplate;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getBudgetCost() {
        return budgetCost;
    }

    public Integer getTimeCost() {
        return timeCost;
    }

    public String getRequiredItemName() {
        return requiredItemName;
    }

    public Integer getRequiredItemQuantity() {
        return requiredItemQuantity;
    }

    public BigDecimal getBaseSuccessProbability() {
        return baseSuccessProbability;
    }

    public BigDecimal getNursingSuccessMultiplier() {
        return nursingSuccessMultiplier;
    }

    public BigDecimal getEngineeringSuccessMultiplier() {
        return engineeringSuccessMultiplier;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public Boolean getActive() {
        return active;
    }
}
