package com.resortapi.resortbooking.dto;

import com.resortapi.resortbooking.entity.Room;

public record RoomDto(
        Long id,
        String roomNumber,
        Long roomTypeId,
        String roomType,
        String status) {

    public static RoomDto from(Room room) {
        return new RoomDto(
                room.getId(),
                room.getRoomNumber(),
                room.getRoomType().getId(),
                room.getRoomType().getName(),
                room.getStatus().name());
    }
}
 