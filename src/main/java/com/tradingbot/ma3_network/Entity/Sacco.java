package com.tradingbot.ma3_network.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tradingbot.ma3_network.Enum.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "saccos")
public class Sacco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "registration_number", nullable = false, unique = true)
    private String registrationNumber;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Column(nullable = false)
    private String tier;

    @Column(name = "max_vehicles", nullable = false)
    private int maxVehicles;

    @Column(name = "monthly_fee", nullable = false)
    private Double monthlyFee; // Changed to Double (Capital D)

    @Column(name = "setup_fee")
    private Double setupFee = 5000.0; // Changed to Double (Capital D)

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false)
    private SubscriptionStatus subscriptionStatus;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}