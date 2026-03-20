package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByVehicleId(Long vehicleId);
    List<Expense> findByCrewIdAndExpenseDateBetweenOrderByExpenseDateDesc(Long crewId, LocalDateTime startOfDay, LocalDateTime endOfDay);
}