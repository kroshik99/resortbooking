package com.resortapi.resortbooking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record RescheduleBookingRequest(
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @Positive int numGuests) {
}
