package com.tradingbot.ma3_network.Dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FinalizeTripRequest {
    private int        passengerCount;
    private BigDecimal totalRevenue;

    // These feed directly into trip.fuelExpense and trip.otherExpenses
    // so the Owner dashboard can calculate real net profit
    private BigDecimal fuelExpense   = BigDecimal.ZERO;
    private BigDecimal otherExpenses = BigDecimal.ZERO; // police, stage fees, etc.
    private Double     distanceKm;
}