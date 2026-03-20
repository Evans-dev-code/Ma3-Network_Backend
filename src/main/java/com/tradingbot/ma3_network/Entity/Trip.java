package com.tradingbot.ma3_network.Entity;

import com.tradingbot.ma3_network.Enum.TripStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "trips", indexes = {
        @Index(name = "idx_trip_vehicle_status", columnList = "vehicle_id, status"),
        @Index(name = "idx_trip_start_time", columnList = "start_time"),
        @Index(name = "idx_trip_driver", columnList = "driver_id")
})
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @Column(name = "route_name", nullable = false)
    private String routeName;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "passenger_count")
    private Integer passengerCount = 0;

    @Column(name = "total_revenue")
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    // ── WEALTH PROTECTOR: Expense Tracking ───────────────────────────────
    @Column(name = "distance_km")
    private Double distanceKm;

    /** Diesel/petrol cost for this trip */
    @Column(name = "fuel_expense", nullable = false)
    private BigDecimal fuelExpense = BigDecimal.ZERO;

    /** Police, stage fees, parking, or any other deduction */
    @Column(name = "other_expenses", nullable = false)
    private BigDecimal otherExpenses = BigDecimal.ZERO;
    // ─────────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    // ── Audit Trail ───────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    // ─────────────────────────────────────────────────────────────────────


    // ══ BUSINESS LOGIC HELPERS ═══════════════════════════════════════════

    /**
     * The "take-home" figure the Owner actually cares about.
     * Net Profit = Revenue - Fuel - Other Expenses
     */
    public BigDecimal getNetProfit() {
        BigDecimal revenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
        BigDecimal fuel = fuelExpense != null ? fuelExpense : BigDecimal.ZERO;
        BigDecimal other = otherExpenses != null ? otherExpenses : BigDecimal.ZERO;
        return revenue.subtract(fuel).subtract(other);
    }

    /**
     * Total expenses for this trip (useful for the cost breakdown chart).
     */
    public BigDecimal getTotalExpenses() {
        BigDecimal fuel = fuelExpense != null ? fuelExpense : BigDecimal.ZERO;
        BigDecimal other = otherExpenses != null ? otherExpenses : BigDecimal.ZERO;
        return fuel.add(other);
    }
}