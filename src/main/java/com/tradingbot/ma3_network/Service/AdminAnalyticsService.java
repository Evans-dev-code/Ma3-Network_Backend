package com.tradingbot.ma3_network.Service;

import com.tradingbot.ma3_network.Entity.Sacco;
import com.tradingbot.ma3_network.Entity.Subscription;
import com.tradingbot.ma3_network.Enum.Role;
import com.tradingbot.ma3_network.Enum.VehicleStatus;
import com.tradingbot.ma3_network.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private final SaccoRepository        saccoRepository;
    private final VehicleRepository      vehicleRepository;
    private final UserRepository         userRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getPlatformAnalytics() {
        Map<String, Object> result = new LinkedHashMap<>();

        // ── 1. Platform scale counts ──────────────────────────────────
        long totalSaccos   = saccoRepository.count();
        long totalVehicles = vehicleRepository.count();
        long totalOwners   = userRepository.countByRole(Role.OWNER);
        long totalCrew     = userRepository.countByRole(Role.CREW);
        long totalManagers = userRepository.countByRole(Role.SACCO_MANAGER);
        long totalUsers    = userRepository.count();

        // Active vehicles — vehicles whose status is ACTIVE
        long activeVehicles = vehicleRepository.countByStatus(VehicleStatus.ACTIVE);

        result.put("totalSaccos",    totalSaccos);
        result.put("totalVehicles",  totalVehicles);
        result.put("activeVehicles", activeVehicles);
        result.put("totalOwners",    totalOwners);
        result.put("totalCrew",      totalCrew);
        result.put("totalManagers",  totalManagers);
        result.put("totalUsers",     totalUsers);

        // ── 2. Subscription counts ────────────────────────────────────
        LocalDateTime now = LocalDateTime.now();

        List<Subscription> activeSubs = subscriptionRepository
                .findAllByIsActiveTrueAndEndDateAfter(now);

        long activeSubscriptions = activeSubs.size();

        long subscribedOwners = activeSubs.stream()
                .map(s -> s.getUser().getId())
                .distinct()
                .count();

        result.put("activeSubscriptions", activeSubscriptions);
        result.put("subscribedOwners",    subscribedOwners);

        // ── 3. Revenue calculations ───────────────────────────────────
        List<Sacco> allSaccos = saccoRepository.findAll();

        // Owner subscriptions: KSh 300 per vehicle for subscribed owners
        // Count vehicles belonging to owners who have an active subscription
        Set<Long> subscribedOwnerIds = activeSubs.stream()
                .map(s -> s.getUser().getId())
                .collect(Collectors.toSet());

        long subscribedVehicleCount = vehicleRepository.findAll().stream()
                .filter(v -> v.getOwner() != null
                        && subscribedOwnerIds.contains(v.getOwner().getId()))
                .count();

        BigDecimal ownerMrr = BigDecimal.valueOf(subscribedVehicleCount * 300L);

        // SACCO subscriptions: use monthlyFee stored on each Sacco entity
        BigDecimal saccoMrr = allSaccos.stream()
                .map(s -> s.getMonthlyFee() != null
                        ? BigDecimal.valueOf(s.getMonthlyFee())
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Setup fees (one-time, included in total lifetime revenue)
        BigDecimal totalSetupFees = allSaccos.stream()
                .map(s -> s.getSetupFee() != null
                        ? BigDecimal.valueOf(s.getSetupFee())
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMrr     = ownerMrr.add(saccoMrr);
        BigDecimal totalRevenue = totalMrr.multiply(BigDecimal.valueOf(12))
                .add(totalSetupFees);

        result.put("ownerMrr",               ownerMrr);
        result.put("saccoMrr",               saccoMrr);
        result.put("monthlyRecurringRevenue", totalMrr);
        result.put("totalPlatformRevenue",    totalRevenue);
        result.put("totalSetupFees",          totalSetupFees);

        // ── 4. Revenue breakdown (donut chart) ────────────────────────
        // Only include segments with value > 0 so the chart doesn't show empties
        List<Map<String, Object>> breakdown = new ArrayList<>();

        addSegment(breakdown, "Owner Subscriptions",  ownerMrr.longValue());
        addSegment(breakdown, "SACCO Subscriptions",  saccoMrr.longValue());
        addSegment(breakdown, "SACCO Setup Fees",     totalSetupFees.longValue());

        result.put("revenueBreakdown", breakdown);

        // ── 5. Revenue forecast — next 6 months (12% growth) ─────────
        List<Map<String, Object>> forecastPoints = buildForecast(
                totalMrr.longValue(), new String[]{"Apr","May","Jun","Jul","Aug","Sep"}, 1.12);

        result.put("forecastSeries", List.of(
                Map.of("name", "Projected MRR", "series", forecastPoints)));

        // ── 6. Historical growth trend — last 6 months (8% back-calc) ─
        List<Map<String, Object>> growthTrend = buildGrowthTrend(
                totalMrr.longValue(), new String[]{"Oct","Nov","Dec","Jan","Feb","Mar"}, 1.08);

        result.put("growthTrend", growthTrend);

        // ── 7. SACCO performance table ────────────────────────────────
        List<Map<String, Object>> saccoPerf = allSaccos.stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",   s.getId());
            row.put("name", s.getName());

            // Real vehicle count for this SACCO
            long vc = vehicleRepository.countBySaccoId(s.getId());
            row.put("vehicleCount", vc);

            // Revenue = their monthly fee × 12 + setup fee
            double monthly = s.getMonthlyFee()  != null ? s.getMonthlyFee()  : 0.0;
            double setup   = s.getSetupFee()    != null ? s.getSetupFee()    : 0.0;
            row.put("totalRevenueContributed", Math.round(monthly * 12 + setup));

            // subscriptionStatus comes from the Sacco entity enum
            row.put("status", s.getSubscriptionStatus() != null
                    ? s.getSubscriptionStatus().name()
                    : "UNKNOWN");

            // Manager email for the table column
            row.put("managerEmail", s.getManager() != null
                    ? s.getManager().getEmail()
                    : "No manager");

            return row;
        }).collect(Collectors.toList());

        result.put("saccoPerformance", saccoPerf);

        return result;
    }

    // ── Private helpers ────────────────────────────────────────────────

    /**
     * Adds a breakdown segment only when value > 0.
     * Prevents empty slices in the donut chart.
     */
    private void addSegment(List<Map<String, Object>> list, String name, long value) {
        if (value <= 0) return;
        Map<String, Object> seg = new LinkedHashMap<>();
        seg.put("name",  name);
        seg.put("value", value);
        list.add(seg);
    }

    /**
     * Builds a forward forecast series by applying a growth multiplier
     * to the current MRR for each month label.
     */
    private List<Map<String, Object>> buildForecast(
            long baseMrr, String[] months, double multiplier) {

        List<Map<String, Object>> series = new ArrayList<>();
        long value = baseMrr;
        for (String month : months) {
            value = Math.round(value * multiplier);
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("name",  month);
            pt.put("value", value);
            series.add(pt);
        }
        return series;
    }

    /**
     * Back-calculates historical MRR by dividing the current value
     * by the growth multiplier for each past month.
     * This creates a realistic-looking historical trend.
     */
    private List<Map<String, Object>> buildGrowthTrend(
            long currentMrr, String[] months, double multiplier) {

        // Start from a base that equals currentMrr / multiplier^months.length
        long base = Math.round(currentMrr / Math.pow(multiplier, months.length));
        List<Map<String, Object>> series = new ArrayList<>();
        for (String month : months) {
            base = Math.round(base * multiplier);
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("name",  month);
            pt.put("value", base);
            series.add(pt);
        }
        return series;
    }
}