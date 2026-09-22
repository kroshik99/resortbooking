package com.resortapi.resortbooking.dto;

public record DashboardStats(
        long bookingsThisMonth,
        long checkInsToday,
        long checkOutsToday,
        long occupiedNow,
        long totalRooms) {
}
