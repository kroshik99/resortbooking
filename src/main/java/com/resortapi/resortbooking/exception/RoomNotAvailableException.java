package com.resortapi.resortbooking.exception;

import java.time.LocalDate;

public class RoomNotAvailableException extends RuntimeException {

    public RoomNotAvailableException(String roomTypeName, LocalDate checkIn, LocalDate checkOut) {
        super("No " + roomTypeName + " room is free from " + checkIn + " to " + checkOut);
    }
}
