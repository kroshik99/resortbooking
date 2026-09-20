package com.resortapi.resortbooking.dto;

public record AvailabilityOptionDto(
        Long roomTypeId,
        String roomType,
        int capacity,
        long nights,
        String totalPrice,
        String currency,
        int roomsLeft) {
}
