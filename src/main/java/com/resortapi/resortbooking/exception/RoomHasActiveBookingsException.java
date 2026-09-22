package com.resortapi.resortbooking.exception;

public class RoomHasActiveBookingsException extends RuntimeException {

    public RoomHasActiveBookingsException(String roomNumber) {
        super("Room " + roomNumber + " has an active booking that hasn't checked out yet, "
                + "so it cannot be sent for maintenance");
    }
}
