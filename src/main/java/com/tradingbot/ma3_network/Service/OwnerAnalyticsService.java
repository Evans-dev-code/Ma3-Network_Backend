package com.tradingbot.ma3_network.Service;

import com.tradingbot.ma3_network.Dto.AnalyticsDashboardResponse;
import com.tradingbot.ma3_network.Dto.AnalyticsDashboardResponse.*;
import com.tradingbot.ma3_network.Entity.Vehicle;
import com.tradingbot.ma3_network.Enum.TripStatus;
import com.tradingbot.ma3_network.Enum.VehicleStatus;
import com.tradingbot.ma3_network.Repository.ExpenseRepository;
import com.tradingbot.ma3_network.Repository.MaintenanceRepository;
import com.tradingbot.ma3_network.Repository.TripRepository;
import com.tradingbot.ma3_network.Repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerAnalyticsService {

    private final VehicleRepository     vehicleRepository;
    private final TripRepository        tripRepository;
    private final MaintenanceRepository maintenanceRepository;
    // 🚨 Injected the Expense Repository to fetch crew logs!
    private final ExpenseRepository     expenseRepository;

    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse getDashboardAnalytics(
            String ownerEmail,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {

        // ── 1. Fetch fleet
        List<Vehicle> myVehicles = vehicleRepository.findByOwnerEmail(ownerEmail);
        if (myVehicles.isEmpty()) {
            return buildEmptyResponse();
        }

        List<Long> vehicleIds = myVehicles.stream()
                .map(Vehicle::getId)
                .collect(Collectors.toList());

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd   = LocalDate.now().atTime(LocalTime.MAX);

        // ── 2. Fleet summary
        long activeCount = myVehicles.stream()
                .filter(v -> v.getStatus() == VehicleStatus.ACTIVE)
                .count();

        long completedTrips = tripRepository.countByVehicleIdInAndStatus(
                vehicleIds, TripStatus.COMPLETED);

        // ── 3. NEW MATH: Decoupled Revenue vs Expenses ────────────────────

        // Gross comes strictly from TRIPS
        BigDecimal totalGrossRevenue = getOrZero(
                tripRepository.getFleetGrossRevenue(vehicleIds, TripStatus.COMPLETED, rangeStart, rangeEnd));

        // Expenses come strictly from EXPENSE LOGGER & MAINTENANCE
        BigDecimal totalFuel = getOrZero(
                expenseRepository.getFleetFuelExpense(vehicleIds, rangeStart, rangeEnd));

        BigDecimal totalOther = getOrZero(
                expenseRepository.getFleetOtherExpenses(vehicleIds, rangeStart, rangeEnd));

        BigDecimal totalMaintenance = getOrZero(
                maintenanceRepository.getTotalMaintenanceCost(vehicleIds, rangeStart, rangeEnd));

        // Calculate Totals and Net Profit dynamically
        BigDecimal totalExpenses = totalFuel.add(totalOther).add(totalMaintenance);
        BigDecimal totalNetProfit = totalGrossRevenue.subtract(totalExpenses);

        // ── 4. Today's net profit (Dynamic Math) ──────────────────────────
        BigDecimal todayGross = getOrZero(
                tripRepository.getFleetGrossRevenue(vehicleIds, TripStatus.COMPLETED, todayStart, todayEnd));
        BigDecimal todayExpenses = getOrZero(
                expenseRepository.getFleetTotalExpenses(vehicleIds, todayStart, todayEnd));
        BigDecimal todayNetProfit = todayGross.subtract(todayExpenses);

        // ── 5. Period trips (for in-memory chart builders)
        List<com.tradingbot.ma3_network.Entity.Trip> periodTrips =
                tripRepository.findByVehicleIdInAndStatusAndStartTimeBetween(
                        vehicleIds, TripStatus.COMPLETED, rangeStart, rangeEnd);

        // ── 6. Per-vehicle performance
        List<VehiclePerformance> vehiclePerformances =
                buildVehiclePerformances(myVehicles, todayStart, todayEnd);

        // ── 7. Charts
        List<ChartData> expenseBreakdown = buildExpenseBreakdown(totalFuel, totalOther, totalMaintenance);
        List<ChartData> maintenanceCostByType = buildMaintenanceCostByType(vehicleIds, rangeStart, rangeEnd);
        List<ChartData> routePerformance = buildRoutePerformance(periodTrips);
        List<ChartSeriesData> weeklyTrend = buildWeeklyTrend(vehicleIds, rangeStart, rangeEnd);

        // ── 8. Alerts
        List<MaintenanceAlert> maintenanceAlerts = buildMaintenanceAlerts(myVehicles);
        List<ComplianceAlert> complianceAlerts = buildComplianceAlerts(ownerEmail);

        return AnalyticsDashboardResponse.builder()
                .totalVehicles((int) myVehicles.size())
                .activeVehicles(activeCount)
                .totalTrips((int) completedTrips)
                .totalGrossRevenue(totalGrossRevenue)
                .totalNetProfit(totalNetProfit)
                .totalExpenses(totalExpenses)
                .totalFuelExpense(totalFuel)
                .totalOtherExpenses(totalOther)
                .totalMaintenanceCost(totalMaintenance)
                .todayNetProfit(todayNetProfit)
                .vehiclePerformances(vehiclePerformances)
                .expenseBreakdown(expenseBreakdown)
                .maintenanceCostByType(maintenanceCostByType)
                .routePerformance(routePerformance)
                .weeklyTrend(weeklyTrend)
                .maintenanceAlerts(maintenanceAlerts)
                .complianceAlerts(complianceAlerts)
                .build();
    }

    @Transactional(readOnly = true)
    public AlertSummary getAlertSummary(String ownerEmail) {
        List<Vehicle> myVehicles = vehicleRepository.findByOwnerEmail(ownerEmail);
        return AlertSummary.builder()
                .maintenanceAlerts(buildMaintenanceAlerts(myVehicles))
                .complianceAlerts(buildComplianceAlerts(ownerEmail))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PRIVATE BUILDERS
    // ══════════════════════════════════════════════════════════════════════

    private List<VehiclePerformance> buildVehiclePerformances(
            List<Vehicle> vehicles,
            LocalDateTime todayStart,
            LocalDateTime todayEnd) {

        return vehicles.stream().map(v -> {
            // NEW MATH: Calculate individual vehicle profit correctly
            BigDecimal vTodayRevenue = getOrZero(
                    tripRepository.getDailyGrossRevenueForVehicle(v.getId(), TripStatus.COMPLETED, todayStart, todayEnd));
            BigDecimal vTodayExpenses = getOrZero(
                    expenseRepository.getDailyTotalForVehicle(v.getId(), todayStart, todayEnd));

            BigDecimal todayProfit = vTodayRevenue.subtract(vTodayExpenses);

            BigDecimal target = v.getDailyTarget() != null
                    ? v.getDailyTarget()
                    : BigDecimal.valueOf(8000);

            double targetPct = target.compareTo(BigDecimal.ZERO) > 0
                    ? todayProfit
                    .divide(target, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue()
                    : 0.0;

            return VehiclePerformance.builder()
                    .vehicleId(v.getId())
                    .plateNumber(v.getPlateNumber())
                    .route(v.getRoute())
                    .status(v.getStatus().name())
                    .dailyTarget(target)
                    .todayNetProfit(todayProfit)
                    .targetAchievementPercent(Math.min(targetPct, 100.0))
                    .serviceDue(v.isServiceDue())
                    .serviceHealthPercent(v.getServiceHealthPercent())
                    .kmSinceLastService(v.getKmSinceLastService())
                    .complianceExpiringSoon(v.isComplianceExpiringSoon())
                    .ntsaExpiry(v.getNtsaExpiry())
                    .insuranceExpiry(v.getInsuranceExpiry())
                    .tlbExpiry(v.getTlbExpiry())
                    .build();
        }).collect(Collectors.toList());
    }

    private List<MaintenanceAlert> buildMaintenanceAlerts(List<Vehicle> vehicles) {
        return vehicles.stream()
                .filter(Vehicle::isServiceDue)
                .map(v -> MaintenanceAlert.builder()
                        .vehicleId(v.getId())
                        .plateNumber(v.getPlateNumber())
                        .kmSinceLastService(v.getKmSinceLastService())
                        .serviceInterval(v.getServiceInterval())
                        .serviceHealthPercent(v.getServiceHealthPercent())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ComplianceAlert> buildComplianceAlerts(String ownerEmail) {
        return vehicleRepository
                .findVehiclesWithExpiringCompliance(ownerEmail, LocalDate.now().plusDays(30))
                .stream()
                .map(v -> ComplianceAlert.builder()
                        .vehicleId(v.getId())
                        .plateNumber(v.getPlateNumber())
                        .ntsaExpiry(v.getNtsaExpiry())
                        .insuranceExpiry(v.getInsuranceExpiry())
                        .tlbExpiry(v.getTlbExpiry())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ChartData> buildExpenseBreakdown(
            BigDecimal fuel, BigDecimal other, BigDecimal maintenance) {
        return Arrays.asList(
                ChartData.builder().name("Fuel").value(fuel).build(),
                ChartData.builder().name("Operational").value(other).build(),
                ChartData.builder().name("Maintenance").value(maintenance).build()
        );
    }

    private List<ChartData> buildMaintenanceCostByType(
            List<Long> vehicleIds, LocalDateTime start, LocalDateTime end) {
        return maintenanceRepository
                .getMaintenanceCostByType(vehicleIds, start, end)
                .stream()
                .map(row -> ChartData.builder()
                        .name(row[0].toString())
                        .value((BigDecimal) row[1])
                        .build())
                .collect(Collectors.toList());
    }

    private List<ChartData> buildRoutePerformance(
            List<com.tradingbot.ma3_network.Entity.Trip> trips) {
        return trips.stream()
                .filter(t -> t.getRouteName() != null)
                .collect(Collectors.groupingBy(
                        com.tradingbot.ma3_network.Entity.Trip::getRouteName,
                        Collectors.reducing(BigDecimal.ZERO,
                                com.tradingbot.ma3_network.Entity.Trip::getNetProfit,
                                BigDecimal::add)))
                .entrySet().stream()
                .map(e -> ChartData.builder()
                        .name(e.getKey())
                        .value(e.getValue())
                        .build())
                .sorted(Comparator.comparing(ChartData::getValue).reversed())
                .collect(Collectors.toList());
    }

    private List<ChartSeriesData> buildWeeklyTrend(
            List<Long> vehicleIds, LocalDateTime start, LocalDateTime end) {

        List<String> dayOrder = Arrays.asList(
                "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

        List<com.tradingbot.ma3_network.Entity.Trip> weekTrips =
                tripRepository.findByVehicleIdInAndStatusAndStartTimeBetween(
                                vehicleIds, TripStatus.COMPLETED, start, end)
                        .stream()
                        .filter(t -> t.getStartTime() != null)
                        .collect(Collectors.toList());

        Map<String, BigDecimal> revenueByDay = weekTrips.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getStartTime().getDayOfWeek()
                                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> getOrZero(t.getTotalRevenue()),
                                BigDecimal::add)));

        Map<String, BigDecimal> profitByDay = weekTrips.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getStartTime().getDayOfWeek()
                                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        Collectors.reducing(BigDecimal.ZERO,
                                com.tradingbot.ma3_network.Entity.Trip::getNetProfit,
                                BigDecimal::add)));

        List<ChartData> revenueSeries = dayOrder.stream()
                .map(day -> ChartData.builder()
                        .name(day)
                        .value(revenueByDay.getOrDefault(day, BigDecimal.ZERO))
                        .build())
                .collect(Collectors.toList());

        List<ChartData> profitSeries = dayOrder.stream()
                .map(day -> ChartData.builder()
                        .name(day)
                        .value(profitByDay.getOrDefault(day, BigDecimal.ZERO))
                        .build())
                .collect(Collectors.toList());

        return Arrays.asList(
                ChartSeriesData.builder().name("Gross Revenue").series(revenueSeries).build(),
                ChartSeriesData.builder().name("Net Profit").series(profitSeries).build()
        );
    }

    private BigDecimal getOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private AnalyticsDashboardResponse buildEmptyResponse() {
        return AnalyticsDashboardResponse.builder()
                .totalVehicles(0).activeVehicles(0).totalTrips(0)
                .totalGrossRevenue(BigDecimal.ZERO).totalNetProfit(BigDecimal.ZERO)
                .totalExpenses(BigDecimal.ZERO).totalFuelExpense(BigDecimal.ZERO)
                .totalOtherExpenses(BigDecimal.ZERO).totalMaintenanceCost(BigDecimal.ZERO)
                .todayNetProfit(BigDecimal.ZERO)
                .vehiclePerformances(Collections.emptyList())
                .expenseBreakdown(Collections.emptyList())
                .maintenanceCostByType(Collections.emptyList())
                .routePerformance(Collections.emptyList())
                .weeklyTrend(Collections.emptyList())
                .maintenanceAlerts(Collections.emptyList())
                .complianceAlerts(Collections.emptyList())
                .build();
    }
}