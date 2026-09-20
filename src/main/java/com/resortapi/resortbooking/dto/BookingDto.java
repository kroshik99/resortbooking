package com.resortapi.resortbooking.dto;

import com.resortapi.resortbooking.entity.Booking;

import java.time.LocalDate;

public record BookingDto(
        String reference,
        String roomNumber,
        String roomType,
        String guestName,
        LocalDate checkIn,
        LocalDate checkOut,
        long nights,
        int numGuests,
        String totalPrice,
        String currency,
        String status) {

    /** Must be called inside the service transaction: room, roomType and guest are LAZY. */
    public static BookingDto from(Booking booking) {
        return new BookingDto(
                booking.getReference(),
                booking.getRoom().getRoomNumber(),
                booking.getRoom().getRoomType().getName(),
                booking.getGuest().getFullName(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.nights(),
                booking.getNumGuests(),
                booking.getTotalPrice().toPlainString(),
                "PHP",
                booking.getStatus().name());
    }
}
