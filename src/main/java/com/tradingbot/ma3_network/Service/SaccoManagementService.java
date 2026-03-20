package com.tradingbot.ma3_network.Service;

import com.tradingbot.ma3_network.Dto.CrewAssignmentRequest;
import com.tradingbot.ma3_network.Dto.VehicleRegistrationRequest;
import com.tradingbot.ma3_network.Entity.Sacco;
import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Entity.Vehicle;
import com.tradingbot.ma3_network.Enum.Role;
import com.tradingbot.ma3_network.Enum.SubscriptionStatus;
import com.tradingbot.ma3_network.Enum.VehicleStatus;
import com.tradingbot.ma3_network.Repository.SaccoRepository;
import com.tradingbot.ma3_network.Repository.UserRepository;
import com.tradingbot.ma3_network.Repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaccoManagementService {

    private final SaccoRepository   saccoRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository    userRepository;
    private final PasswordEncoder   passwordEncoder;

    // ── Register SACCO ────────────────────────────────────────────────────
    public Sacco registerSacco(Sacco sacco) {
        sacco.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        return saccoRepository.save(sacco);
    }

    // ── Fleet list ────────────────────────────────────────────────────────
    public List<Vehicle> getSaccoFleet(String managerEmail) {
        return vehicleRepository.findBySaccoManagerEmail(managerEmail).stream()
                .filter(v -> v.getStatus() != VehicleStatus.INACTIVE)
                .toList();
    }

    // ── Register vehicle with crew ────────────────────────────────────────
    // NOTE: This method is kept for backward compatibility but the
    // SaccoController.registerVehicleWithCrew() handles all registration
    // inline with full validation. This service method is used when called
    // programmatically from other services if needed.
    @Transactional
    public Vehicle registerVehicleWithCrew(VehicleRegistrationRequest req,
                                           String managerEmail) {

        vehicleRepository.findByDriverEmail(req.getDriverEmail()).ifPresent(v -> {
            throw new RuntimeException(
                    "Driver " + req.getDriverEmail()
                            + " is already assigned to " + v.getPlateNumber());
        });

        if (req.hasConductor()) {
            vehicleRepository.findByConductorEmail(req.getConductorEmail()).ifPresent(v -> {
                throw new RuntimeException(
                        "Conductor " + req.getConductorEmail()
                                + " is already assigned to " + v.getPlateNumber());
            });
        }

        Sacco sacco = saccoRepository.findByManagerEmail(managerEmail)
                .orElseThrow(() -> new RuntimeException(
                        "No SACCO found for manager: " + managerEmail));

        User owner = userRepository.findByEmail(req.getOwnerEmail())
                .orElseThrow(() -> new RuntimeException(
                        "Owner with email " + req.getOwnerEmail()
                                + " not found. The owner must have a registered account."));

        User driver = findOrCreateCrewUser(
                req.getDriverFirstName(),
                req.getDriverLastName(),
                req.getDriverEmail(),
                req.getDriverPhone(),
                req.getDriverPassword()
        );

        User conductor = null;
        if (req.hasConductor()) {
            if (req.getConductorFirstName() == null || req.getConductorFirstName().isBlank())
                throw new RuntimeException("Conductor first name is required.");
            if (req.getConductorLastName() == null || req.getConductorLastName().isBlank())
                throw new RuntimeException("Conductor last name is required.");
            if (req.getConductorPassword() == null || req.getConductorPassword().isBlank())
                throw new RuntimeException("Conductor password is required.");

            conductor = findOrCreateCrewUser(
                    req.getConductorFirstName(),
                    req.getConductorLastName(),
                    req.getConductorEmail(),
                    req.getConductorPhone(),
                    req.getConductorPassword()
            );
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(req.getPlateNumber());
        vehicle.setRoute(req.getRoute());
        vehicle.setCapacity(req.getCapacity());
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle.setNtsaExpiry(req.getNtsaExpiry());
        vehicle.setInsuranceExpiry(req.getInsuranceExpiry());
        vehicle.setTlbExpiry(req.getTlbExpiry());
        vehicle.setDailyTarget(
                BigDecimal.valueOf(req.getDailyTarget() > 0 ? req.getDailyTarget() : 8000L));
        vehicle.setCurrentMileage(req.getCurrentMileage());
        vehicle.setLastServiceMileage(req.getCurrentMileage());
        vehicle.setServiceInterval(5000);
        vehicle.setSacco(sacco);
        vehicle.setOwner(owner);
        vehicle.setDriver(driver);
        vehicle.setConductor(conductor);

        return vehicleRepository.save(vehicle);
    }

    // ── Reassign crew to existing vehicle ─────────────────────────────────
    @Transactional
    public Vehicle assignCrewToVehicle(Long vehicleId,
                                       CrewAssignmentRequest request,
                                       String managerEmail) {

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (!vehicle.getSacco().getManager().getEmail().equals(managerEmail)) {
            throw new RuntimeException("Unauthorized: You do not manage this vehicle's SACCO.");
        }

        vehicleRepository.findByDriverEmail(request.getDriverEmail())
                .filter(v -> !v.getId().equals(vehicleId))
                .ifPresent(v -> {
                    throw new RuntimeException(
                            "Driver " + request.getDriverEmail()
                                    + " is already assigned to " + v.getPlateNumber());
                });

        if (request.getConductorEmail() != null && !request.getConductorEmail().isBlank()) {
            vehicleRepository.findByConductorEmail(request.getConductorEmail())
                    .filter(v -> !v.getId().equals(vehicleId))
                    .ifPresent(v -> {
                        throw new RuntimeException(
                                "Conductor " + request.getConductorEmail()
                                        + " is already assigned to " + v.getPlateNumber());
                    });
        }

        User newDriver = findOrCreateCrewUser(
                request.getDriverFirstName(),
                request.getDriverLastName(),
                request.getDriverEmail(),
                request.getDriverPhone(),
                null  // no password change on reassign
        );

        User newConductor = null;
        if (request.getConductorEmail() != null && !request.getConductorEmail().isBlank()) {
            newConductor = findOrCreateCrewUser(
                    request.getConductorFirstName(),
                    request.getConductorLastName(),
                    request.getConductorEmail(),
                    request.getConductorPhone(),
                    null
            );
        }

        vehicle.setDriver(newDriver);
        vehicle.setConductor(newConductor);

        return vehicleRepository.save(vehicle);
    }

    // ── Soft-delete vehicle ───────────────────────────────────────────────
    @Transactional
    public void removeVehicleFromSacco(Long vehicleId, String managerEmail) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (!vehicle.getSacco().getManager().getEmail().equals(managerEmail)) {
            throw new RuntimeException(
                    "Unauthorized: You cannot remove a vehicle that does not belong to your SACCO.");
        }

        vehicle.setDriver(null);
        vehicle.setConductor(null);
        vehicle.setStatus(VehicleStatus.INACTIVE);
        vehicleRepository.save(vehicle);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Resolves an existing CREW user by email, or creates a new one.
     * Phone uniqueness is checked before insert to give a clean error
     * instead of a raw SQL constraint violation.
     * Password is only set on creation — never overwritten on existing users.
     */
    private User findOrCreateCrewUser(String firstName, String lastName,
                                      String email, String phone,
                                      String rawPassword) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    // Guard against duplicate phone number before attempting insert
                    if (phone != null && !phone.isBlank()
                            && userRepository.existsByPhoneNumber(phone)) {
                        throw new RuntimeException(
                                "Phone number " + phone
                                        + " is already registered to another account.");
                    }

                    User u = new User();
                    u.setFirstName(firstName);
                    u.setLastName(lastName);
                    u.setEmail(email);
                    u.setPhoneNumber(phone);
                    u.setPasswordHash(passwordEncoder.encode(
                            rawPassword != null && !rawPassword.isBlank()
                                    ? rawPassword
                                    : "Default@123"
                    ));
                    u.setRole(Role.CREW);
                    return userRepository.save(u);
                });
    }
}