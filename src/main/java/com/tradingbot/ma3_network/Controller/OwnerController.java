package com.tradingbot.ma3_network.Controller;

import com.tradingbot.ma3_network.Dto.AnalyticsDashboardResponse;
import com.tradingbot.ma3_network.Dto.AnalyticsDashboardResponse.AlertSummary;
import com.tradingbot.ma3_network.Entity.MaintenanceRecord;
import com.tradingbot.ma3_network.Repository.MaintenanceRepository;
import com.tradingbot.ma3_network.Repository.VehicleRepository;
import com.tradingbot.ma3_network.Service.OwnerAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerAnalyticsService analyticsService;
    private final VehicleRepository     vehicleRepository;
    private final MaintenanceRepository maintenanceRepository;

    // ══════════════════════════════════════════════════════════════════════
    //  ANALYTICS
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsDashboardResponse> getDashboardAnalytics(
            Principal principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        // Default: last 30 days so historical trips always appear.
        // Previously defaulted to "Monday this week" which cut off older data.
        LocalDateTime rangeStart = from != null
                ? from
                : LocalDateTime.now().minusDays(30).toLocalDate().atStartOfDay();

        LocalDateTime rangeEnd = to != null
                ? to
                : LocalDateTime.now();

        return ResponseEntity.ok(
                analyticsService.getDashboardAnalytics(
                        principal.getName(), rangeStart, rangeEnd));
    }

    @GetMapping("/alerts")
    public ResponseEntity<AlertSummary> getAlerts(Principal principal) {
        return ResponseEntity.ok(analyticsService.getAlertSummary(principal.getName()));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FLEET MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Returns vehicles as safe DTOs — no raw entity exposure,
     * no lazy-loading exceptions.
     * LinkedHashMap used throughout: preserves field order in JSON
     * and accepts mixed-type values (Long, String, BigDecimal, boolean, double).
     */
    @GetMapping("/fleet")
    public ResponseEntity<List<Map<String, Object>>> getMyFleet(Principal principal) {
        List<Map<String, Object>> fleet = vehicleRepository
                .findByOwnerEmail(principal.getName())
                .stream()
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",              v.getId());
                    m.put("plateNumber",     v.getPlateNumber());
                    m.put("route",           v.getRoute());
                    m.put("status",          v.getStatus().name());
                    m.put("capacity",        v.getCapacity());
                    m.put("dailyTarget",     v.getDailyTarget());
                    m.put("currentMileage",  v.getCurrentMileage());
                    m.put("serviceInterval", v.getServiceInterval());
                    m.put("ntsaExpiry",      v.getNtsaExpiry().toString());
                    m.put("insuranceExpiry", v.getInsuranceExpiry().toString());
                    m.put("tlbExpiry",       v.getTlbExpiry().toString());
                    m.put("serviceDue",      v.isServiceDue());
                    m.put("serviceHealth",   v.getServiceHealthPercent());
                    return m;
                })
                .toList();

        return ResponseEntity.ok(fleet);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TARGET MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════

    @PutMapping("/vehicle/{id}/target")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateDailyTarget(
            @PathVariable Long id,
            @RequestBody Map<String, BigDecimal> payload,
            Principal principal) {

        BigDecimal newTarget = payload.get("target");

        if (newTarget == null || newTarget.compareTo(BigDecimal.ZERO) <= 0) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Target must be a positive number");
            return ResponseEntity.badRequest().body(err);
        }

        return vehicleRepository.findById(id)
                .filter(v -> v.getOwner().getEmail().equals(principal.getName()))
                .map(v -> {
                    v.setDailyTarget(newTarget);
                    vehicleRepository.save(v);

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("vehicleId",   v.getId());
                    body.put("plateNumber", v.getPlateNumber());
                    body.put("newTarget",   v.getDailyTarget());
                    body.put("message",     "Daily target updated to KSh " + newTarget);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("error", "Vehicle not found or access denied");
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MAINTENANCE
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/maintenance")
    @Transactional
    public ResponseEntity<Map<String, Object>> logMaintenance(
            @RequestBody MaintenanceRecord record,
            Principal principal) {

        if (record.getVehicle() == null || record.getVehicle().getId() == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Vehicle ID is required");
            return ResponseEntity.badRequest().body(err);
        }

        return vehicleRepository.findById(record.getVehicle().getId())
                .filter(v -> v.getOwner().getEmail().equals(principal.getName()))
                .map(v -> {
                    if (record.getMileageAtService() != null
                            && record.getMileageAtService() > v.getCurrentMileage()) {
                        v.setCurrentMileage(record.getMileageAtService());
                        v.setLastServiceMileage(record.getMileageAtService());
                        vehicleRepository.save(v);
                    }
                    record.setVehicle(v);
                    MaintenanceRecord saved = maintenanceRepository.save(record);

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("id",      saved.getId());
                    body.put("vehicle", v.getPlateNumber());
                    body.put("type",    saved.getMaintenanceType().name());
                    body.put("cost",    saved.getCost());
                    body.put("message", "Maintenance record saved successfully");
                    return ResponseEntity.<Map<String, Object>>status(HttpStatus.CREATED).body(body);
                })
                .orElseGet(() -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("error", "Vehicle not found or access denied");
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
                });
    }

    @GetMapping("/vehicle/{id}/maintenance")
    public ResponseEntity<List<MaintenanceRecord>> getMaintenanceHistory(
            @PathVariable Long id,
            Principal principal) {

        return vehicleRepository.findById(id)
                .filter(v -> v.getOwner().getEmail().equals(principal.getName()))
                .map(v -> ResponseEntity.ok(
                        maintenanceRepository.findByVehicleIdOrderByServiceDateDesc(id)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }
}