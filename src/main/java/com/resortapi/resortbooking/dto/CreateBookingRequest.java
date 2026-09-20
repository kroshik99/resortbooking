package com.resortapi.resortbooking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreateBookingRequest(
        @NotNull Long roomTypeId,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @Positive int numGuests,
        @NotNull @Valid GuestDetails guest) {
}
