package com.resortapi.resortbooking.exception;

public class InvalidBookingDatesException extends RuntimeException {

    public InvalidBookingDatesException(String message) {
        super(message);
    }
}
