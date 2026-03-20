package com.tradingbot.ma3_network.Service;

import com.tradingbot.ma3_network.Dto.TripResponseDto;
import com.tradingbot.ma3_network.Entity.Trip;
import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Enum.TripStatus;
import com.tradingbot.ma3_network.Repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;

    // ── Start Trip ────────────────────────────────────────────────────────
    @Transactional
    public TripResponseDto startTrip(Trip trip, User driver) {
        trip.setStatus(TripStatus.BOARDING);
        trip.setStartTime(LocalDateTime.now());
        trip.setDriver(driver);
        // Ensure expense fields default to zero — never null
        if (trip.getFuelExpense()   == null) trip.setFuelExpense(BigDecimal.ZERO);
        if (trip.getOtherExpenses() == null) trip.setOtherExpenses(BigDecimal.ZERO);
        Trip saved = tripRepository.save(trip);
        return TripResponseDto.from(saved);
    }

    // ── Update Status ─────────────────────────────────────────────────────
    @Transactional
    public TripResponseDto updateTripStatus(Long tripId, TripStatus status) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));
        trip.setStatus(status);
        if (status == TripStatus.COMPLETED) {
            trip.setEndTime(LocalDateTime.now());
        }
        return TripResponseDto.from(tripRepository.save(trip));
    }

    // ── Finalize Trip ─────────────────────────────────────────────────────
    // This is the KEY method — it writes fuel + other expenses into the
    // trips table so the Owner dashboard can read real net profit.
    @Transactional
    public TripResponseDto finalizeTrip(
            Long tripId,
            int passengerCount,
            BigDecimal totalRevenue,
            BigDecimal fuelExpense,
            BigDecimal otherExpenses,
            Double distanceKm) {

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));

        trip.setStatus(TripStatus.COMPLETED);
        trip.setEndTime(LocalDateTime.now());
        trip.setPassengerCount(passengerCount);
        trip.setTotalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO);

        // Write expenses into the trip row — owner dashboard reads these
        trip.setFuelExpense(fuelExpense     != null ? fuelExpense   : BigDecimal.ZERO);
        trip.setOtherExpenses(otherExpenses != null ? otherExpenses : BigDecimal.ZERO);

        if (distanceKm != null) trip.setDistanceKm(distanceKm);

        return TripResponseDto.from(tripRepository.save(trip));
    }

    // ── Active trips by vehicle ───────────────────────────────────────────
    public List<Trip> getActiveTripsByVehicle(Long vehicleId) {
        return tripRepository.findByVehicleIdAndStatus(vehicleId, TripStatus.EN_ROUTE);
    }

    // ── Today's trips for driver ──────────────────────────────────────────
    public List<TripResponseDto> getTodaysTripsForDriver(Long driverId) {
        LocalDateTime start = LocalDateTime.now().with(java.time.LocalTime.MIN);
        LocalDateTime end   = LocalDateTime.now().with(java.time.LocalTime.MAX);
        return tripRepository
                .findByDriverIdAndStartTimeBetweenOrderByStartTimeDesc(driverId, start, end)
                .stream()
                .map(TripResponseDto::from)
                .collect(Collectors.toList());
    }
}