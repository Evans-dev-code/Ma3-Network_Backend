package com.tradingbot.ma3_network.Service;

import com.tradingbot.ma3_network.Entity.Expense;
import com.tradingbot.ma3_network.Entity.Trip;
import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Enum.ExpenseCategory;
import com.tradingbot.ma3_network.Repository.ExpenseRepository;
import com.tradingbot.ma3_network.Repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final TripRepository    tripRepository;

    /**
     * Logs an expense AND syncs it into the active trip's fuel/other fields
     * so the Owner dashboard immediately reflects real costs.
     *
     * If no active trip exists for the vehicle, the expense is still saved
     * to the expenses table for audit purposes.
     */
    @Transactional
    public Expense logExpense(Expense expense, User crewMember) {
        expense.setCrew(crewMember);
        Expense saved = expenseRepository.save(expense);

        // Sync into the active trip on this vehicle
        if (expense.getVehicle() != null && expense.getVehicle().getId() != null) {
            syncExpenseToActiveTrip(
                    expense.getVehicle().getId(),
                    expense.getCategory(),
                    expense.getAmount()
            );
        }

        return saved;
    }

    /**
     * Finds the most recent non-completed trip for the vehicle and adds
     * the expense amount to the appropriate field on the trip row.
     */
    private void syncExpenseToActiveTrip(
            Long vehicleId,
            ExpenseCategory category,
            BigDecimal amount) {

        Optional<com.tradingbot.ma3_network.Entity.Trip> activeTrip =
                tripRepository.findByVehicleIdAndStatus(
                                vehicleId, com.tradingbot.ma3_network.Enum.TripStatus.EN_ROUTE)
                        .stream().findFirst();

        if (activeTrip.isEmpty()) {
            activeTrip = tripRepository.findByVehicleIdAndStatus(
                            vehicleId, com.tradingbot.ma3_network.Enum.TripStatus.BOARDING)
                    .stream().findFirst();
        }

        activeTrip.ifPresent(trip -> {
            // FUEL goes to fuelExpense — everything else goes to otherExpenses
            // SQUAD is treated as STAGE_FEE (legacy alias)
            if (category == ExpenseCategory.FUEL) {
                BigDecimal current = trip.getFuelExpense() != null
                        ? trip.getFuelExpense() : BigDecimal.ZERO;
                trip.setFuelExpense(current.add(amount));
            } else {
                // STAGE_FEE, SQUAD, POLICE, MAINTENANCE, OTHER all go to otherExpenses
                BigDecimal current = trip.getOtherExpenses() != null
                        ? trip.getOtherExpenses() : BigDecimal.ZERO;
                trip.setOtherExpenses(current.add(amount));
            }
            tripRepository.save(trip);
        });
    }

    public List<Expense> getTodaysExpensesForCrew(Long crewId) {
        LocalDateTime start = LocalDateTime.now().with(java.time.LocalTime.MIN);
        LocalDateTime end   = LocalDateTime.now().with(java.time.LocalTime.MAX);
        return expenseRepository
                .findByCrewIdAndExpenseDateBetweenOrderByExpenseDateDesc(crewId, start, end);
    }
}