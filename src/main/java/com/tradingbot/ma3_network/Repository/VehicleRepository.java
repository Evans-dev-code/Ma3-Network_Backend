package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.Vehicle;
import com.tradingbot.ma3_network.Enum.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByDriverEmail(String driverEmail);
    Optional<Vehicle> findByConductorEmail(String conductorEmail);
    Optional<Vehicle> findByPlateNumber(String plateNumber);

    List<Vehicle> findBySaccoId(Long saccoId);
    List<Vehicle> findByOwnerId(Long ownerId);
    List<Vehicle> findByOwnerEmail(String ownerEmail);
    List<Vehicle> findByOwnerEmailAndStatus(String ownerEmail, VehicleStatus status);
    List<Vehicle> findBySaccoManagerEmail(String managerEmail);

    @Query("""
        SELECT v FROM Vehicle v
        LEFT JOIN v.conductor c
        WHERE v.driver.email = :email
           OR (c IS NOT NULL AND c.email = :email)
        """)
    Optional<Vehicle> findByCrewEmail(@Param("email") String email);
    @Query("""
            SELECT v FROM Vehicle v
            WHERE v.owner.email = :ownerEmail
              AND (v.currentMileage - v.lastServiceMileage) >= (v.serviceInterval * 0.9)
            """)
    List<Vehicle> findVehiclesDueForService(@Param("ownerEmail") String ownerEmail);

    @Query("""
            SELECT v FROM Vehicle v
            WHERE v.owner.email = :ownerEmail
              AND (v.ntsaExpiry     <= :warningDate
                OR v.insuranceExpiry <= :warningDate
                OR v.tlbExpiry      <= :warningDate)
            """)
    List<Vehicle> findVehiclesWithExpiringCompliance(
            @Param("ownerEmail")   String ownerEmail,
            @Param("warningDate")  LocalDate warningDate);

    // ── Count helpers ─────────────────────────────────────────────────────
    long countByOwnerEmail(String ownerEmail);
    long countByOwnerEmailAndStatus(String ownerEmail, VehicleStatus status);
    long countByStatus(VehicleStatus status);
    long countBySaccoId(Long saccoId);

    @Query("""
            SELECT COUNT(v) FROM Vehicle v
            WHERE v.owner.id IN (
                SELECT s.user.id FROM Subscription s
                WHERE s.isActive = true
                  AND s.endDate > CURRENT_TIMESTAMP
            )
            """)
    long countByOwnerWithActiveSubscription();
}