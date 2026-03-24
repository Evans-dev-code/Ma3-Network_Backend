package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.Trip;
import com.tradingbot.ma3_network.Enum.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    // ── Basic Lookups ─────────────────────────────────────────────────────
    List<Trip> findByVehicleIdAndStatus(Long vehicleId, TripStatus status);
    List<Trip> findByDriverId(Long driverId);
    List<Trip> findByDriverIdAndStartTimeBetweenOrderByStartTimeDesc(
            Long driverId, LocalDateTime start, LocalDateTime end);

    // ── Bulk Fleet Queries ────────────────────────────────────────────────
    List<Trip> findByVehicleIdInAndStatus(List<Long> vehicleIds, TripStatus status);

    List<Trip> findByVehicleIdInAndStartTimeBetween(
            List<Long> vehicleIds, LocalDateTime start, LocalDateTime end);

    List<Trip> findByVehicleIdInAndStatusAndStartTimeBetween(
            List<Long> vehicleIds, TripStatus status,
            LocalDateTime start, LocalDateTime end);

    // ── Analytics: Revenue & Profit ───────────────────────────────────────

    @Query("""
            SELECT COALESCE(SUM(t.totalRevenue - t.fuelExpense - t.otherExpenses), 0)
            FROM Trip t
            WHERE t.vehicle.id = :vehicleId
              AND t.status = :status
              AND t.startTime BETWEEN :start AND :end
            """)
    BigDecimal getDailyNetProfitForVehicle(
            @Param("vehicleId") Long vehicleId,
            @Param("status")    TripStatus status,
            @Param("start")     LocalDateTime start,
            @Param("end")       LocalDateTime end);

    @Query("""
            SELECT COALESCE(SUM(t.totalRevenue - t.fuelExpense - t.otherExpenses), 0)
            FROM Trip t
            WHERE t.vehicle.id IN :vehicleIds
              AND t.status = :status
              AND t.startTime BETWEEN :start AND :end
            """)
    BigDecimal getFleetNetProfit(
            @Param("vehicleIds") List<Long> vehicleIds,
            @Param("status")     TripStatus status,
            @Param("start")      LocalDateTime start,
            @Param("end")        LocalDateTime end);

    @Query("""
            SELECT COALESCE(SUM(t.totalRevenue), 0)
            FROM Trip t
            WHERE t.vehicle.id IN :vehicleIds
              AND t.status = :status
              AND t.startTime BETWEEN :start AND :end
            """)
    BigDecimal getFleetGrossRevenue(
            @Param("vehicleIds") List<Long> vehicleIds,
            @Param("status")     TripStatus status,
            @Param("start")      LocalDateTime start,
            @Param("end")        LocalDateTime end);

    // NEW: Gets raw revenue for a single vehicle (Used for per-vehicle profit math)
    @Query("""
            SELECT COALESCE(SUM(t.totalRevenue), 0)
            FROM Trip t
            WHERE t.vehicle.id = :vehicleId
              AND t.status = :status
              AND t.startTime BETWEEN :start AND :end
            """)
    BigDecimal getDailyGrossRevenueForVehicle(
            @Param("vehicleId") Long vehicleId,
            @Param("status")    TripStatus status,
            @Param("start")     LocalDateTime start,
            @Param("end")       LocalDateTime end);

    @Query("""
            SELECT COALESCE(SUM(t.fuelExpense), 0)
            FROM Trip t
            WHERE t.vehicle.id IN :vehicleIds
              AND t.status = :status
              AND t.startTime BETWEEN :start AND :end
            """)
    BigDecimal getFleetTotalFuelExpense(
            @Param("vehicleIds") List<Long> vehicleIds,
            @Param("status")     TripStatus status,
            @Param("start")      LocalDateTime start,
            @Param("end")        LocalDateTime end);

    @Query("""
            SELECT COALESCE(SUM(t.otherExpenses), 0)
            FROM Trip t
            WHERE t.vehicle.id IN :vehicleIds
              AND t.status = :status
              AND t.startTime BETWEEN :start AND :end
            """)
    BigDecimal getFleetTotalOtherExpenses(
            @Param("vehicleIds") List<Long> vehicleIds,
            @Param("status")     TripStatus status,
            @Param("start")      LocalDateTime start,
            @Param("end")        LocalDateTime end);

    @Query("""
            SELECT FUNCTION('DAYOFWEEK', t.startTime),
                   COALESCE(SUM(t.totalRevenue), 0),
                   COALESCE(SUM(t.totalRevenue - t.fuelExpense - t.otherExpenses), 0)
            FROM Trip t
            WHERE t.vehicle.id IN :vehicleIds
              AND t.status = :status
              AND t.startTime BETWEEN :start AND :end
            GROUP BY FUNCTION('DAYOFWEEK', t.startTime)
            ORDER BY FUNCTION('DAYOFWEEK', t.startTime)
            """)
    List<Object[]> getWeeklyTrendByVehicleIds(
            @Param("vehicleIds") List<Long> vehicleIds,
            @Param("status")     TripStatus status,
            @Param("start")      LocalDateTime start,
            @Param("end")        LocalDateTime end);

    // ── Trip Count ────────────────────────────────────────────────────────
    long countByVehicleIdInAndStatus(List<Long> vehicleIds, TripStatus status);
}