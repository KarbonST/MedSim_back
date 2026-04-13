package ru.vstu.medsim.economy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "clinic_room_problem_templates")
public class ClinicRoomProblemTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_room_template_id", nullable = false)
    private ClinicRoomTemplate clinicRoom;

    @Column(name = "problem_number", nullable = false)
    private Integer problemNumber;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 30)
    private ProblemSeverity severity;

    @Column(name = "ignore_penalty", nullable = false, precision = 12, scale = 2)
    private BigDecimal ignorePenalty;

    @Column(name = "stage_number", nullable = false)
    private Integer stageNumber;

    @Column(name = "budget_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal budgetCost;

    @Column(name = "time_cost", nullable = false)
    private Integer timeCost;

    @Column(name = "required_item_name", length = 200)
    private String requiredItemName;

    @Column(name = "required_item_quantity", nullable = false)
    private Integer requiredItemQuantity;

    protected ClinicRoomProblemTemplate() {
    }

    public ClinicRoomProblemTemplate(
            ClinicRoomTemplate clinicRoom,
            Integer problemNumber,
            String title,
            ProblemSeverity severity,
            BigDecimal ignorePenalty
    ) {
        this.clinicRoom = clinicRoom;
        this.problemNumber = problemNumber;
        this.title = title;
        this.severity = severity;
        this.ignorePenalty = ignorePenalty;
        this.stageNumber = 2;
        this.budgetCost = BigDecimal.ONE.setScale(2);
        this.timeCost = 1;
        this.requiredItemQuantity = 0;
    }

    public Long getId() {
        return id;
    }

    public ClinicRoomTemplate getClinicRoom() {
        return clinicRoom;
    }

    public Integer getProblemNumber() {
        return problemNumber;
    }

    public String getTitle() {
        return title;
    }

    public ProblemSeverity getSeverity() {
        return severity;
    }

    public BigDecimal getIgnorePenalty() {
        return ignorePenalty;
    }

    public Integer getStageNumber() {
        return stageNumber;
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
}
