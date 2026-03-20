package com.tradingbot.ma3_network.Dto;

import com.tradingbot.ma3_network.Entity.Trip;
import com.tradingbot.ma3_network.Enum.TripStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TripResponseDto {

    private Long          id;
    private String        routeName;
    private TripStatus    status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int           passengerCount;
    private BigDecimal    totalRevenue;
    private BigDecimal    fuelExpense;
    private BigDecimal    otherExpenses;
    private BigDecimal    netProfit;
    private Double        distanceKm;
    // Vehicle info — safe flat fields, no entity reference
    private Long          vehicleId;
    private String        plateNumber;
    private String        route;
    // Driver info — safe flat fields
    private Long          driverId;
    private String        driverName;

    public static TripResponseDto from(Trip t) {
        BigDecimal revenue = orZero(t.getTotalRevenue());
        BigDecimal fuel    = orZero(t.getFuelExpense());
        BigDecimal other   = orZero(t.getOtherExpenses());

        return TripResponseDto.builder()
                .id(t.getId())
                .routeName(t.getRouteName())
                .status(t.getStatus())
                .startTime(t.getStartTime())
                .endTime(t.getEndTime())
                .passengerCount(t.getPassengerCount() != null ? t.getPassengerCount() : 0)
                .totalRevenue(revenue)
                .fuelExpense(fuel)
                .otherExpenses(other)
                .netProfit(revenue.subtract(fuel).subtract(other))
                .distanceKm(t.getDistanceKm())
                // Vehicle — null-safe
                .vehicleId(t.getVehicle() != null ? t.getVehicle().getId() : null)
                .plateNumber(t.getVehicle() != null ? t.getVehicle().getPlateNumber() : null)
                .route(t.getVehicle() != null ? t.getVehicle().getRoute() : null)
                // Driver — null-safe
                .driverId(t.getDriver() != null ? t.getDriver().getId() : null)
                .driverName(t.getDriver() != null
                        ? t.getDriver().getFirstName() + " " + t.getDriver().getLastName()
                        : null)
                .build();
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}