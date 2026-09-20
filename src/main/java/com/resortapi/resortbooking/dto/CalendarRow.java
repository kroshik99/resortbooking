package com.resortapi.resortbooking.dto;

import java.util.List;

/** One room and everything booked into it during the displayed week. */
public record CalendarRow(
        String roomNumber,
        String roomType,
        String roomStatus,
        List<CalendarBar> bars) {
}
