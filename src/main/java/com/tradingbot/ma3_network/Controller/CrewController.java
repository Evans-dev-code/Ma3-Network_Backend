package com.tradingbot.ma3_network.Controller;

import com.tradingbot.ma3_network.Dto.ExpenseResponseDto;
import com.tradingbot.ma3_network.Dto.FinalizeTripRequest;
import com.tradingbot.ma3_network.Dto.TripResponseDto;
import com.tradingbot.ma3_network.Entity.Expense;
import com.tradingbot.ma3_network.Entity.Trip;
import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Enum.TripStatus;
import com.tradingbot.ma3_network.Repository.RouteRepository;
import com.tradingbot.ma3_network.Service.ExpenseService;
import com.tradingbot.ma3_network.Service.LocationBroadcastService;
import com.tradingbot.ma3_network.Service.TripService;
import com.tradingbot.ma3_network.Repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/crew")
@RequiredArgsConstructor
public class CrewController {

    private final TripService              tripService;
    private final ExpenseService           expenseService;
    private final LocationBroadcastService locationService;
    private final VehicleRepository        vehicleRepository;
    private final RouteRepository routeRepository;


    @GetMapping("/my-vehicle")
    public ResponseEntity<Map<String, Object>> getMyVehicle(
            @AuthenticationPrincipal User crewMember) {

        return vehicleRepository.findByCrewEmail(crewMember.getEmail())
                .map(v -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("vehicleId",      v.getId());
                    body.put("plateNumber",    v.getPlateNumber());
                    body.put("route",          v.getRoute());
                    body.put("dailyTarget",    v.getDailyTarget());
                    body.put("currentMileage", v.getCurrentMileage());

                    // Tell the frontend which role this crew member has on this vehicle
                    // so the UI can show/hide driver-only actions (e.g. start trip)
                    boolean isDriver = v.getDriver() != null
                            && crewMember.getEmail().equals(v.getDriver().getEmail());
                    body.put("crewRole", isDriver ? "DRIVER" : "CONDUCTOR");
                    body.put("driverId",   v.getDriver()    != null ? v.getDriver().getId()    : null);
                    body.put("conductorId",v.getConductor() != null ? v.getConductor().getId() : null);

                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .<Map<String, Object>>build());
    }

    // ── Trip: Start ───────────────────────────────────────────────────────
    @PostMapping("/trip/start")
    public ResponseEntity<TripResponseDto> startTrip(
            @RequestBody Trip trip,
            @AuthenticationPrincipal User driver) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tripService.startTrip(trip, driver));
    }

    // ── Trip: Update Status ───────────────────────────────────────────────
    @PatchMapping("/trip/{tripId}/status")
    public ResponseEntity<TripResponseDto> updateTripStatus(
            @PathVariable Long tripId,
            @RequestParam TripStatus status) {
        return ResponseEntity.ok(tripService.updateTripStatus(tripId, status));
    }

    // ── Trip: Finalize ────────────────────────────────────────────────────
    @PatchMapping("/trip/{tripId}/finalize")
    public ResponseEntity<TripResponseDto> finalizeTrip(
            @PathVariable Long tripId,
            @RequestBody FinalizeTripRequest request) {
        return ResponseEntity.ok(tripService.finalizeTrip(
                tripId,
                request.getPassengerCount(),
                request.getTotalRevenue(),
                request.getFuelExpense(),
                request.getOtherExpenses(),
                request.getDistanceKm()
        ));
    }

    @PostMapping("/expense")
    public ResponseEntity<ExpenseResponseDto> logExpense(
            @RequestBody Expense expense,
            @AuthenticationPrincipal User crewMember) {
        Expense saved = expenseService.logExpense(expense, crewMember);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ExpenseResponseDto.from(saved));
    }

    // ── Location: Broadcast ───────────────────────────────────────────────
    @PostMapping("/vehicle/{vehicleId}/location")
    public ResponseEntity<Void> broadcastLocation(
            @PathVariable Long vehicleId,
            @RequestParam double lat,
            @RequestParam double lng) {
        locationService.broadcastLocation(vehicleId, lat, lng);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/trips/today")
    public ResponseEntity<List<TripResponseDto>> getTodayTrips(
            @AuthenticationPrincipal User crewMember) {

        // Resolve vehicle via crew email (works for both driver + conductor)
        return vehicleRepository.findByCrewEmail(crewMember.getEmail())
                .map(v -> ResponseEntity.ok(
                        tripService.getTodaysTripsForDriver(v.getDriver() != null
                                ? v.getDriver().getId()
                                : crewMember.getId())))
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .<List<TripResponseDto>>build());
    }

    @GetMapping("/expenses/today")
    public ResponseEntity<List<ExpenseResponseDto>> getTodayExpenses(
            @AuthenticationPrincipal User crewMember) {

        return vehicleRepository.findByCrewEmail(crewMember.getEmail())
                .map(v -> {
                    // Use vehicle ID to get all expenses, not just per-crew-member
                    List<ExpenseResponseDto> expenses =
                            expenseService.getTodaysExpensesForCrew(crewMember.getId())
                                    .stream()
                                    .map(ExpenseResponseDto::from)
                                    .toList();
                    return ResponseEntity.ok(expenses);
                })
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .<List<ExpenseResponseDto>>build());
    }

    // ── Routes: Get routes for this crew member's SACCO ───────────────────
    /**
     * GET /api/v1/crew/routes
     * Returns the routes of the SACCO this crew member's vehicle belongs to.
     * Used to populate the route dropdown in the trip logger.
     */
    @GetMapping("/routes")
    public ResponseEntity<List<Map<String, Object>>> getMyRoutes(
            @AuthenticationPrincipal User crewMember) {

        return vehicleRepository.findByCrewEmail(crewMember.getEmail())
                .map(v -> {
                    List<Map<String, Object>> routes = routeRepository
                            .findBySaccoIdOrderByNameAsc(v.getSacco().getId())
                            .stream()
                            .map(r -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("id",         r.getId());
                                m.put("name",       r.getName());
                                m.put("startPoint", r.getStartPoint());
                                m.put("endPoint",   r.getEndPoint());
                                m.put("display",    r.getName() + " — "
                                        + r.getStartPoint() + " → " + r.getEndPoint());
                                return m;
                            })
                            .toList();
                    return ResponseEntity.ok(routes);
                })
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .<List<Map<String, Object>>>build());
    }
}