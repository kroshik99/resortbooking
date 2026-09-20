package com.resortapi.resortbooking.dto;

import com.resortapi.resortbooking.entity.RoomStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateRoomStatusRequest(@NotNull RoomStatus status) {
}
