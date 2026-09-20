package com.resortapi.resortbooking.dto;

import com.resortapi.resortbooking.entity.CheckInRequest;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CheckInRequestDto(
        Long id,
        String bookingReference,
        String guestName,
        String roomNumber,
        String roomType,
        LocalDate checkIn,
        String requestedByName,
        OffsetDateTime requestedAt,
        String status) {

    /** Must be called inside the service transaction: every association here is LAZY. */
    public static CheckInRequestDto from(CheckInRequest request) {
        return new CheckInRequestDto(
                request.getId(),
                request.getBooking().getReference(),
                request.getBooking().getGuest().getFullName(),
                request.getBooking().getRoom().getRoomNumber(),
                request.getBooking().getRoom().getRoomType().getName(),
                request.getBooking().getCheckIn(),
                request.getRequestedBy().getEmail(),
                request.getRequestedAt(),
                request.getStatus().name());
    }
}
