package com.tradingbot.ma3_network.Controller;

import com.tradingbot.ma3_network.Dto.CrewAssignmentRequest;
import com.tradingbot.ma3_network.Dto.VehicleRegistrationRequest;
import com.tradingbot.ma3_network.Entity.Route;
import com.tradingbot.ma3_network.Entity.Sacco;
import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Entity.Vehicle;
import com.tradingbot.ma3_network.Enum.Role;
import com.tradingbot.ma3_network.Enum.VehicleStatus;
import com.tradingbot.ma3_network.Repository.RouteRepository;
import com.tradingbot.ma3_network.Repository.SaccoRepository;
import com.tradingbot.ma3_network.Repository.UserRepository;
import com.tradingbot.ma3_network.Repository.VehicleRepository;
import com.tradingbot.ma3_network.Service.SaccoManagementService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/sacco")
@RequiredArgsConstructor
public class SaccoController {

    private final SaccoManagementService saccoService;
    private final SaccoRepository        saccoRepository;
    private final VehicleRepository      vehicleRepository;
    private final UserRepository         userRepository;
    private final PasswordEncoder        passwordEncoder;
    private final RouteRepository routeRepository;

    // ── SACCO: Register (admin-facing) ────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<Sacco> registerSacco(@RequestBody Sacco sacco) {
        return ResponseEntity.ok(saccoService.registerSacco(sacco));
    }

    // ── SACCO: My SACCO info ──────────────────────────────────────────────
    @GetMapping("/my-sacco")
    public ResponseEntity<Map<String, Object>> getMySacco(Principal principal) {
        return saccoRepository.findByManagerEmail(principal.getName())
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",                 s.getId());
                    m.put("name",               s.getName());
                    m.put("registrationNumber", s.getRegistrationNumber());
                    m.put("contactPhone",       s.getContactPhone());
                    m.put("tier",               s.getTier());
                    m.put("maxVehicles",        s.getMaxVehicles());
                    m.put("monthlyFee",         s.getMonthlyFee());
                    m.put("subscriptionStatus", s.getSubscriptionStatus().name());
                    return ResponseEntity.ok(m);
                })
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No SACCO found for this manager")));
    }

    // ── SACCO: Update contact details ─────────────────────────────────────
    @PutMapping("/my-sacco")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateMySacco(
            @RequestBody Map<String, String> body,
            Principal principal) {

        return saccoRepository.findByManagerEmail(principal.getName())
                .map(s -> {
                    if (body.get("name") != null && !body.get("name").isBlank())
                        s.setName(body.get("name").trim());
                    if (body.get("contactPhone") != null && !body.get("contactPhone").isBlank())
                        s.setContactPhone(body.get("contactPhone").trim());
                    saccoRepository.save(s);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("message",      "SACCO details updated");
                    result.put("name",         s.getName());
                    result.put("contactPhone", s.getContactPhone());
                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No SACCO found for this manager")));
    }

    // ── Vehicle: Register with crew ───────────────────────────────────────
    @PostMapping("/vehicle")
    @Transactional
    public ResponseEntity<Map<String, Object>> registerVehicleWithCrew(
            @Valid @RequestBody VehicleRegistrationRequest req,
            Principal principal) {

        // 1 — Resolve SACCO
        Sacco sacco = saccoRepository.findByManagerEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "No SACCO found for manager: " + principal.getName()));

        // 2 — Plate must be unique
        if (vehicleRepository.findByPlateNumber(req.getPlateNumber()).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Plate number " + req.getPlateNumber() + " is already registered."));
        }

        // 3 — Resolve owner (must already have an account)
        User owner = userRepository.findByEmail(req.getOwnerEmail())
                .orElse(null);
        if (owner == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Owner with email " + req.getOwnerEmail()
                                    + " not found. The owner must have a registered account first."));
        }

        // 4 — Driver must not already be assigned to another vehicle
        if (vehicleRepository.findByDriverEmail(req.getDriverEmail()).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Driver " + req.getDriverEmail()
                                    + " is already assigned to another vehicle."));
        }

        // 4b — Driver phone must be unique (only when creating a new user)
        boolean driverExists = userRepository.findByEmail(req.getDriverEmail()).isPresent();
        if (!driverExists && userRepository.existsByPhoneNumber(req.getDriverPhone())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Phone number " + req.getDriverPhone()
                                    + " is already registered to another account."));
        }

        // 5 — Resolve or create driver
        User driver = userRepository.findByEmail(req.getDriverEmail())
                .orElseGet(() -> {
                    User u = new User();
                    u.setFirstName(req.getDriverFirstName());
                    u.setLastName(req.getDriverLastName());
                    u.setEmail(req.getDriverEmail());
                    u.setPhoneNumber(req.getDriverPhone());
                    u.setPasswordHash(passwordEncoder.encode(req.getDriverPassword()));
                    u.setRole(Role.CREW);
                    return userRepository.save(u);
                });

        // 6 — Conductor is optional
        User conductor = null;
        if (req.hasConductor()) {

            // 6a — Conductor must not already be on another vehicle
            if (vehicleRepository.findByConductorEmail(req.getConductorEmail()).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error",
                                "Conductor " + req.getConductorEmail()
                                        + " is already assigned to another vehicle."));
            }

            // 6b — Validate required conductor fields are present
            if (req.getConductorFirstName() == null || req.getConductorFirstName().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Conductor first name is required."));
            }
            if (req.getConductorLastName() == null || req.getConductorLastName().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Conductor last name is required."));
            }
            if (req.getConductorPassword() == null || req.getConductorPassword().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Conductor password is required."));
            }

            // 6c — Conductor phone must be unique (only when creating a new user)
            boolean conductorExists = userRepository.findByEmail(req.getConductorEmail()).isPresent();
            if (!conductorExists && req.getConductorPhone() != null
                    && !req.getConductorPhone().isBlank()
                    && userRepository.existsByPhoneNumber(req.getConductorPhone())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error",
                                "Conductor phone number " + req.getConductorPhone()
                                        + " is already registered to another account."));
            }

            conductor = userRepository.findByEmail(req.getConductorEmail())
                    .orElseGet(() -> {
                        User u = new User();
                        u.setFirstName(req.getConductorFirstName());
                        u.setLastName(req.getConductorLastName());
                        u.setEmail(req.getConductorEmail());
                        u.setPhoneNumber(req.getConductorPhone());
                        u.setPasswordHash(passwordEncoder.encode(req.getConductorPassword()));
                        u.setRole(Role.CREW);
                        return userRepository.save(u);
                    });
        }

        // 7 — Build and persist vehicle
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(req.getPlateNumber());
        vehicle.setRoute(req.getRoute());
        vehicle.setCapacity(req.getCapacity());
        vehicle.setDailyTarget(
                BigDecimal.valueOf(req.getDailyTarget() > 0 ? req.getDailyTarget() : 8000L));
        vehicle.setCurrentMileage(req.getCurrentMileage());
        vehicle.setLastServiceMileage(req.getCurrentMileage());
        vehicle.setServiceInterval(5000);
        vehicle.setNtsaExpiry(req.getNtsaExpiry());
        vehicle.setInsuranceExpiry(req.getInsuranceExpiry());
        vehicle.setTlbExpiry(req.getTlbExpiry());
        vehicle.setSacco(sacco);
        vehicle.setOwner(owner);
        vehicle.setDriver(driver);
        vehicle.setConductor(conductor);  // null when no conductor — column is nullable
        vehicle.setStatus(VehicleStatus.ACTIVE);

        Vehicle saved = vehicleRepository.save(vehicle);

        // 8 — Response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message",        "Vehicle registered successfully");
        response.put("vehicleId",      saved.getId());
        response.put("plateNumber",    saved.getPlateNumber());
        response.put("route",          saved.getRoute());
        response.put("ownerEmail",     owner.getEmail());
        response.put("driverEmail",    driver.getEmail());
        response.put("conductorEmail", conductor != null ? conductor.getEmail() : null);
        response.put("hasConductor",   conductor != null);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Vehicle: Fleet list ───────────────────────────────────────────────
    @GetMapping("/fleet")
    public ResponseEntity<List<Vehicle>> getFleet(Principal principal) {
        return ResponseEntity.ok(saccoService.getSaccoFleet(principal.getName()));
    }

    // ── Vehicle: Reassign crew ────────────────────────────────────────────
    @PutMapping("/vehicle/{id}/crew")
    public ResponseEntity<?> reassignCrew(
            @PathVariable Long id,
            @RequestBody CrewAssignmentRequest request,
            Principal principal) {
        try {
            Vehicle v = saccoService.assignCrewToVehicle(id, request, principal.getName());
            return ResponseEntity.ok("Crew successfully reassigned to " + v.getPlateNumber());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Vehicle: Assign or remove conductor ───────────────────────────────
    @PatchMapping("/vehicle/{vehicleId}/conductor")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateConductor(
            @PathVariable Long vehicleId,
            @RequestBody Map<String, String> body,
            Principal principal) {

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NoSuchElementException("Vehicle not found."));

        Sacco sacco = saccoRepository.findByManagerEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "No SACCO found for manager: " + principal.getName()));

        if (!vehicle.getSacco().getId().equals(sacco.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Vehicle does not belong to your SACCO."));
        }

        String conductorEmail = body.get("conductorEmail");

        if (conductorEmail == null || conductorEmail.isBlank()) {
            vehicle.setConductor(null);
            vehicleRepository.save(vehicle);
            return ResponseEntity.ok(Map.of(
                    "message",      "Conductor removed from vehicle",
                    "vehicleId",    vehicleId,
                    "hasConductor", false
            ));
        }

        Optional<Vehicle> existing = vehicleRepository.findByConductorEmail(conductorEmail);
        if (existing.isPresent() && !existing.get().getId().equals(vehicleId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Conductor " + conductorEmail
                                    + " is already assigned to another vehicle."));
        }

        User conductor = userRepository.findByEmail(conductorEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No user found with email " + conductorEmail
                                + ". Create their account first."));

        vehicle.setConductor(conductor);
        vehicleRepository.save(vehicle);

        return ResponseEntity.ok(Map.of(
                "message",        "Conductor assigned successfully",
                "vehicleId",      vehicleId,
                "conductorEmail", conductor.getEmail(),
                "conductorName",  conductor.getFirstName() + " " + conductor.getLastName(),
                "hasConductor",   true
        ));
    }

    // ── Vehicle: Remove from fleet ────────────────────────────────────────
    @DeleteMapping("/vehicle/{id}")
    public ResponseEntity<?> removeVehicle(
            @PathVariable Long id,
            Principal principal) {
        try {
            saccoService.removeVehicleFromSacco(id, principal.getName());
            return ResponseEntity.ok(
                    "Vehicle successfully retired and removed from the active fleet.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Routes: List all routes for this SACCO ────────────────────────────
    /**
     * GET /api/v1/sacco/routes
     * Returns all routes defined by the logged-in manager's SACCO.
     * Used to populate dropdowns in vehicle registration and trip logger.
     */
    @GetMapping("/routes")
    public ResponseEntity<List<Map<String, Object>>> getRoutes(Principal principal) {
        List<Map<String, Object>> routes = routeRepository
                .findBySaccoManagerEmailOrderByNameAsc(principal.getName())
                .stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",         r.getId());
                    m.put("name",       r.getName());
                    m.put("startPoint", r.getStartPoint());
                    m.put("endPoint",   r.getEndPoint());
                    m.put("display",    r.getName() + " — " + r.getStartPoint()
                            + " → " + r.getEndPoint());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(routes);
    }

// ── Routes: Create ────────────────────────────────────────────────────
    /**
     * POST /api/v1/sacco/routes
     * Body: { "name": "Route 23", "startPoint": "CBD", "endPoint": "Westlands" }
     */
    @PostMapping("/routes")
    @Transactional
    public ResponseEntity<Map<String, Object>> createRoute(
            @RequestBody Map<String, String> body,
            Principal principal) {

        Sacco sacco = saccoRepository.findByManagerEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("No SACCO found."));

        String name       = body.get("name");
        String startPoint = body.get("startPoint");
        String endPoint   = body.get("endPoint");

        if (name == null || name.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Route name is required."));
        if (startPoint == null || startPoint.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Start point is required."));
        if (endPoint == null || endPoint.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "End point is required."));

        if (routeRepository.existsBySaccoIdAndName(sacco.getId(), name.trim())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Route '" + name + "' already exists for your SACCO."));
        }

        Route route = new Route();
        route.setName(name.trim());
        route.setStartPoint(startPoint.trim());
        route.setEndPoint(endPoint.trim());
        route.setSacco(sacco);

        Route saved = routeRepository.save(route);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message",    "Route created successfully",
                "id",         saved.getId(),
                "name",       saved.getName(),
                "startPoint", saved.getStartPoint(),
                "endPoint",   saved.getEndPoint(),
                "display",    saved.getName() + " — "
                        + saved.getStartPoint() + " → " + saved.getEndPoint()
        ));
    }

// ── Routes: Update ────────────────────────────────────────────────────
    /**
     * PUT /api/v1/sacco/routes/{id}
     * Body: { "name": "...", "startPoint": "...", "endPoint": "..." }
     */
    @PutMapping("/routes/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateRoute(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Principal principal) {

        Sacco sacco = saccoRepository.findByManagerEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("No SACCO found."));

        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Route not found."));

        if (!route.getSacco().getId().equals(sacco.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Route does not belong to your SACCO."));
        }

        if (body.get("name")       != null && !body.get("name").isBlank())
            route.setName(body.get("name").trim());
        if (body.get("startPoint") != null && !body.get("startPoint").isBlank())
            route.setStartPoint(body.get("startPoint").trim());
        if (body.get("endPoint")   != null && !body.get("endPoint").isBlank())
            route.setEndPoint(body.get("endPoint").trim());

        routeRepository.save(route);

        return ResponseEntity.ok(Map.of(
                "message",    "Route updated",
                "id",         route.getId(),
                "name",       route.getName(),
                "startPoint", route.getStartPoint(),
                "endPoint",   route.getEndPoint()
        ));
    }

// ── Routes: Delete ────────────────────────────────────────────────────
    /**
     * DELETE /api/v1/sacco/routes/{id}
     */
    @DeleteMapping("/routes/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteRoute(
            @PathVariable Long id,
            Principal principal) {

        Sacco sacco = saccoRepository.findByManagerEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("No SACCO found."));

        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Route not found."));

        if (!route.getSacco().getId().equals(sacco.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Route does not belong to your SACCO."));
        }

        routeRepository.delete(route);
        return ResponseEntity.ok(Map.of(
                "message", "Route deleted",
                "id",      id
        ));
    }
}