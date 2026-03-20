package com.tradingbot.ma3_network.Entity;

import com.tradingbot.ma3_network.Enum.VehicleStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "vehicles", indexes = {
        @Index(name = "idx_vehicle_owner_email", columnList = "owner_id"),
        @Index(name = "idx_vehicle_status", columnList = "status")
})
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plate_number", nullable = false, unique = true)
    private String plateNumber;

    @Column(nullable = false)
    private String route;

    @Column(nullable = false)
    private Integer capacity;

    // ── WEALTH PROTECTOR: Financial Targets ──────────────────────────────
    @Column(name = "daily_target", nullable = false)
    private BigDecimal dailyTarget = BigDecimal.valueOf(8000.00);
    // ─────────────────────────────────────────────────────────────────────

    // ── WEALTH PROTECTOR: Mileage & Service Tracking ─────────────────────
    @Column(name = "current_mileage", nullable = false)
    private Long currentMileage = 0L;

    @Column(name = "last_service_mileage", nullable = false)
    private Long lastServiceMileage = 0L;

    /**
     * How many km between scheduled services.
     * Default: 5,000 km. Owner can adjust per vehicle.
     */
    @Column(name = "service_interval", nullable = false)
    private Integer serviceInterval = 5000;
    // ─────────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleStatus status;

    // ── Compliance & Expiry Dates ─────────────────────────────────────────
    @Column(name = "ntsa_expiry", nullable = false)
    private LocalDate ntsaExpiry;

    @Column(name = "insurance_expiry", nullable = false)
    private LocalDate insuranceExpiry;

    @Column(name = "tlb_expiry", nullable = false)
    private LocalDate tlbExpiry;
    // ─────────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sacco_id", nullable = false)
    private Sacco sacco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conductor_id")
    private User conductor;

    // ── Audit Trail ───────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    // ─────────────────────────────────────────────────────────────────────


    // ══ BUSINESS LOGIC HELPERS ═══════════════════════════════════════════

    /**
     * Returns km driven since the last service.
     */
    public long getKmSinceLastService() {
        return currentMileage - lastServiceMileage;
    }

    /**
     * Returns what % of the service interval has been used up.
     * e.g. 4500km / 5000km = 90%
     */
    public double getServiceHealthPercent() {
        if (serviceInterval == null || serviceInterval == 0) return 0.0;
        return ((double) getKmSinceLastService() / serviceInterval) * 100.0;
    }

    /**
     * True when the vehicle has hit ≥90% of its service interval.
     * This is the trigger for the Maintenance Alert on the Owner dashboard.
     */
    public boolean isServiceDue() {
        return getServiceHealthPercent() >= 90.0;
    }

    /**
     * True if any compliance document expires within the next 30 days.
     */
    public boolean isComplianceExpiringSoon() {
        LocalDate warningDate = LocalDate.now().plusDays(30);
        return ntsaExpiry.isBefore(warningDate)
                || insuranceExpiry.isBefore(warningDate)
                || tlbExpiry.isBefore(warningDate);
    }
}