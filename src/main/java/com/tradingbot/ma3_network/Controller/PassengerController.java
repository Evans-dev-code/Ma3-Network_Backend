package com.tradingbot.ma3_network.Controller;

import com.tradingbot.ma3_network.Entity.Booking;
import com.tradingbot.ma3_network.Entity.User;
import com.tradingbot.ma3_network.Service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/passenger")
@RequiredArgsConstructor
public class PassengerController {

    private final BookingService bookingService;

    @PostMapping("/trip/{tripId}/book")
    public ResponseEntity<Booking> bookSeat(
            @PathVariable Long tripId,
            @RequestBody Booking booking,
            @AuthenticationPrincipal User currentUser) {

        booking.setPassenger(currentUser);
        return ResponseEntity.ok(bookingService.bookSeat(tripId, booking));
    }

    @GetMapping("/trip/{tripId}/bookings")
    public ResponseEntity<List<Booking>> getTripBookings(@PathVariable Long tripId) {
        return ResponseEntity.ok(bookingService.getBookingsForTrip(tripId));
    }
}