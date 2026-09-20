package com.resortapi.resortbooking.dto;

import com.resortapi.resortbooking.entity.RoomType;

public record RoomTypeDto(
        Long id,
        String name,
        int capacity,
        String basePrice,
        String description) {

    public static RoomTypeDto from(RoomType roomType) {
        return new RoomTypeDto(
                roomType.getId(),
                roomType.getName(),
                roomType.getCapacity(),
                roomType.getBasePrice().toPlainString(),
                roomType.getDescription());
    }
}
