package com.resortapi.resortbooking.dto;

/**
 * One booking drawn on the week grid. startCol and span are computed in the service
 * so the template only loops and writes a grid-column style: no date maths in HTML.
 */
public record CalendarBar(
        String reference,
        String guestName,
        String status,
        int startCol,
        int span) {
}
