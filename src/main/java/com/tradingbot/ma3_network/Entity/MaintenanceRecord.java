package com.tradingbot.ma3_network.Entity;

import com.tradingbot.ma3_network.Enum.MaintenanceType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "maintenance_records", indexes = {
        @Index(name = "idx_maintenance_vehicle", columnList = "vehicle_id"),
        @Index(name = "idx_maintenance_date", columnList = "service_date")
})
public class MaintenanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    /**
     * What category of work was done.
     * Drives the cost-breakdown chart on the Owner dashboard.
     * e.g. OIL_CHANGE, TYRES, BRAKES, ELECTRICAL, BODY_WORK, OTHER
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_type", nullable = false)
    private MaintenanceType maintenanceType;

    @Column(nullable = false)
    private String description; // e.g., "Replaced front brake pads - Mwangi Garage"

    @Column(name = "mileage_at_service")
    private Long mileageAtService;

    @Column(nullable = false)
    private BigDecimal cost;

    /** Who did the work — useful for tracking reliable mechanics */
    @Column(name = "performed_by")
    private String performedBy;

    @CreationTimestamp
    @Column(name = "service_date", updatable = false)
    private LocalDateTime serviceDate;
}