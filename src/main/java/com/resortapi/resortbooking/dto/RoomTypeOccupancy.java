package com.resortapi.resortbooking.dto;

/** How many rooms of this type are physically occupied right now (CHECKED_IN), out of the total. */
public record RoomTypeOccupancy(String roomTypeName, int occupied, int total) {
}
