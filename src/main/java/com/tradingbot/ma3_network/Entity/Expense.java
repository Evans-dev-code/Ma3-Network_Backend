package com.tradingbot.ma3_network.Entity;

import com.tradingbot.ma3_network.Enum.ExpenseCategory;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crew_id", nullable = false)
    private User crew;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseCategory category;

    private String description;

    @Column(name = "expense_date", nullable = false)
    private LocalDateTime expenseDate;

    @PrePersist
    protected void onCreate() {
        this.expenseDate = LocalDateTime.now();
    }
}