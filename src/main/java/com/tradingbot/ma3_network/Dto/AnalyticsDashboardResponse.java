package com.tradingbot.ma3_network.Dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDashboardResponse {

    // ══════════════════════════════════════════════════════════════════════
    //  ADMIN FIELDS
    // ══════════════════════════════════════════════════════════════════════

    /** Total platform revenue (MRR + setup fees) */
    private BigDecimal totalPlatformRevenue;

    /** Monthly Recurring Revenue from all active SACCOs */
    private BigDecimal monthlyRecurringRevenue;

    /** Total SACCOs registered on the platform */
    private long totalSaccos;

    /** Total fleet owners registered */
    private long totalOwners;

    /** Admin revenue breakdown chart (Subscriptions vs Setup Fees) */
    private List<ChartData> revenueBreakdown;

    /** Per-SACCO performance summary table */
    private List<SaccoSummary> saccoPerformance;

    // ══════════════════════════════════════════════════════════════════════
    //  OWNER FIELDS — Fleet Summary
    // ══════════════════════════════════════════════════════════════════════

    private int  totalVehicles;
    private long activeVehicles;
    private int  totalTrips;

    // ══════════════════════════════════════════════════════════════════════
    //  OWNER FIELDS — Financial KPIs
    // ══════════════════════════════════════════════════════════════════════

    private BigDecimal totalGrossRevenue;
    private BigDecimal totalNetProfit;
    private BigDecimal totalExpenses;
    private BigDecimal totalFuelExpense;
    private BigDecimal totalOtherExpenses;
    private BigDecimal totalMaintenanceCost;
    private BigDecimal todayNetProfit;

    // ══════════════════════════════════════════════════════════════════════
    //  OWNER FIELDS — Per-Vehicle & Charts
    // ══════════════════════════════════════════════════════════════════════

    private List<VehiclePerformance> vehiclePerformances;
    private List<ChartData>          expenseBreakdown;
    private List<ChartData>          maintenanceCostByType;
    private List<ChartData>          routePerformance;
    private List<ChartSeriesData>    weeklyTrend;

    // ══════════════════════════════════════════════════════════════════════
    //  OWNER FIELDS — Alerts
    // ══════════════════════════════════════════════════════════════════════

    private List<MaintenanceAlert> maintenanceAlerts;
    private List<ComplianceAlert>  complianceAlerts;

    // ══════════════════════════════════════════════════════════════════════
    //  INNER DTOs — SHARED
    // ══════════════════════════════════════════════════════════════════════

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChartData {
        private String     name;
        private BigDecimal value;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChartSeriesData {
        private String          name;
        private List<ChartData> series;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INNER DTOs — ADMIN ONLY
    // ══════════════════════════════════════════════════════════════════════

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SaccoSummary {
        private String     name;
        private int        vehicleCount;
        private BigDecimal totalRevenueContributed;
        private String     status;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INNER DTOs — OWNER ONLY
    // ══════════════════════════════════════════════════════════════════════

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class VehiclePerformance {
        private Long       vehicleId;
        private String     plateNumber;
        private String     route;
        private String     status;
        private BigDecimal dailyTarget;
        private BigDecimal todayNetProfit;
        private double     targetAchievementPercent;
        private boolean    serviceDue;
        private double     serviceHealthPercent;
        private long       kmSinceLastService;
        private boolean    complianceExpiringSoon;
        private LocalDate  ntsaExpiry;
        private LocalDate  insuranceExpiry;
        private LocalDate  tlbExpiry;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MaintenanceAlert {
        private Long   vehicleId;
        private String plateNumber;
        private long   kmSinceLastService;
        private int    serviceInterval;
        private double serviceHealthPercent;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ComplianceAlert {
        private Long      vehicleId;
        private String    plateNumber;
        private LocalDate ntsaExpiry;
        private LocalDate insuranceExpiry;
        private LocalDate tlbExpiry;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AlertSummary {
        private List<MaintenanceAlert> maintenanceAlerts;
        private List<ComplianceAlert>  complianceAlerts;
    }
}