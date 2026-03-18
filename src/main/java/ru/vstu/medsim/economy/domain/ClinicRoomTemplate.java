package ru.vstu.medsim.economy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "clinic_room_templates")
public class ClinicRoomTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "base_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseIncome;

    protected ClinicRoomTemplate() {
    }

    public ClinicRoomTemplate(String code, String name, Integer sortOrder, BigDecimal baseIncome) {
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
        this.baseIncome = baseIncome;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public BigDecimal getBaseIncome() {
        return baseIncome;
    }
}
