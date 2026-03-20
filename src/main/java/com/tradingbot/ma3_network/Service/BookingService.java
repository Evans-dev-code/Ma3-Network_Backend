package com.tradingbot.ma3_network.Service;

import com.tradingbot.ma3_network.Entity.Booking;
import com.tradingbot.ma3_network.Entity.Trip;
import com.tradingbot.ma3_network.Enum.PaymentStatus;
import com.tradingbot.ma3_network.Repository.BookingRepository;
import com.tradingbot.ma3_network.Repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;

    public Booking bookSeat(Long tripId, Booking booking) {
        if (bookingRepository.existsByTripIdAndSeatNumber(tripId, booking.getSeatNumber())) {
            throw new RuntimeException("Seat already booked");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        booking.setTrip(trip);
        booking.setPaymentStatus(PaymentStatus.PENDING);

        return bookingRepository.save(booking);
    }

    public List<Booking> getBookingsForTrip(Long tripId) {
        return bookingRepository.findByTripId(tripId);
    }
}