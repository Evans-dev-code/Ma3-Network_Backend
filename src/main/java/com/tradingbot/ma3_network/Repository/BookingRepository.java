package com.tradingbot.ma3_network.Repository;

import com.tradingbot.ma3_network.Entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByTripId(Long tripId);
    List<Booking> findByPassengerId(Long passengerId);
    boolean existsByTripIdAndSeatNumber(Long tripId, Integer seatNumber);
}