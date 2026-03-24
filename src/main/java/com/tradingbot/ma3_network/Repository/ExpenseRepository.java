package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByVehicleId(Long vehicleId);

    List<Expense> findByCrewIdAndExpenseDateBetweenOrderByExpenseDateDesc(
            Long crewId, LocalDateTime startOfDay, LocalDateTime endOfDay);

    // ── NEW MATH QUERIES: Summing Crew Logs ───────────────────────────────

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.vehicle.id IN :vehicleIds AND e.category = 'FUEL' AND e.expenseDate BETWEEN :start AND :end")
    BigDecimal getFleetFuelExpense(
            @Param("vehicleIds") List<Long> vehicleIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.vehicle.id IN :vehicleIds AND e.category != 'FUEL' AND e.expenseDate BETWEEN :start AND :end")
    BigDecimal getFleetOtherExpenses(
            @Param("vehicleIds") List<Long> vehicleIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.vehicle.id IN :vehicleIds AND e.expenseDate BETWEEN :start AND :end")
    BigDecimal getFleetTotalExpenses(
            @Param("vehicleIds") List<Long> vehicleIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.vehicle.id = :vehicleId AND e.expenseDate BETWEEN :start AND :end")
    BigDecimal getDailyTotalForVehicle(
            @Param("vehicleId") Long vehicleId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}