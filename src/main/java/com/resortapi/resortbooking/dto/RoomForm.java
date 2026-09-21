package com.resortapi.resortbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Form-binding object for the admin rooms page. Mutable, for th:field. */
public class RoomForm {

    @NotBlank(message = "Please enter a room number")
    @Size(max = 10)
    private String roomNumber;

    @NotNull(message = "Please choose a room type")
    private Long roomTypeId;

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public Long getRoomTypeId() {
        return roomTypeId;
    }

    public void setRoomTypeId(Long roomTypeId) {
        this.roomTypeId = roomTypeId;
    }
}
