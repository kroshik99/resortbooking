package com.resortapi.resortbooking.dto;

/** What the details and review pages need to show a price. */
public record QuoteView(
        Long roomTypeId,
        String roomType,
        int capacity,
        long nights,
        String totalPrice,
        String currency) {
}
