package com.tradingbot.ma3_network.Dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDate;

@Data
public class VehicleRegistrationRequest {

    // ── Vehicle details ───────────────────────────────────────────────────

    @NotBlank(message = "Plate number is required")
    private String plateNumber;

    @NotBlank(message = "Route is required")
    private String route;

    @Min(value = 1, message = "Capacity must be at least 1")
    private int capacity;

    // dailyTarget defaults to 0 if omitted — controller applies 8000 fallback.
    // @PositiveOrZero allows 0 (meaning "not set yet"); controller fills the default.
    @PositiveOrZero(message = "Daily target must be zero or a positive number")
    private long dailyTarget;

    // A brand-new vehicle starts at 0 km — @Positive would incorrectly reject that.
    @PositiveOrZero(message = "Current mileage must be zero or a positive number")
    private long currentMileage;

    // ── Compliance dates ──────────────────────────────────────────────────

    @NotNull(message = "NTSA expiry date is required")
    private LocalDate ntsaExpiry;

    @NotNull(message = "Insurance expiry date is required")
    private LocalDate insuranceExpiry;

    @NotNull(message = "TLB expiry date is required")
    private LocalDate tlbExpiry;

    // ── Owner ─────────────────────────────────────────────────────────────

    @NotBlank(message = "Owner email is required")
    private String ownerEmail;

    // ── Driver (required) ─────────────────────────────────────────────────

    @NotBlank(message = "Driver first name is required")
    private String driverFirstName;

    @NotBlank(message = "Driver last name is required")
    private String driverLastName;

    @NotBlank(message = "Driver email is required")
    private String driverEmail;

    @NotBlank(message = "Driver phone is required")
    @Pattern(regexp = "^[0-9]{10,12}$", message = "Enter a valid driver phone number")
    private String driverPhone;

    @NotBlank(message = "Driver password is required")
    private String driverPassword;

    // ── Conductor (optional) ──────────────────────────────────────────────
    // No @NotBlank / @NotNull on any conductor field.
    // If conductorEmail is absent the vehicle is saved with conductor = null.
    // The SACCO can assign one later via PATCH /vehicle/{id}/conductor.

    private String conductorFirstName;
    private String conductorLastName;
    private String conductorEmail;

    // Allows empty string (no conductor) OR a valid 10–12 digit number.
    @Pattern(
            regexp  = "^$|^[0-9]{10,12}$",
            message = "Enter a valid conductor phone number"
    )
    private String conductorPhone;

    private String conductorPassword;

    // ── Helper ────────────────────────────────────────────────────────────
    public boolean hasConductor() {
        return conductorEmail != null && !conductorEmail.isBlank();
    }
}