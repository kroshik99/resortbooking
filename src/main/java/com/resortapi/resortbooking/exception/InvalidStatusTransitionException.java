package com.resortapi.resortbooking.exception;

import com.resortapi.resortbooking.entity.BookingStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(BookingStatus from, BookingStatus to) {
        super("A booking cannot move from " + from + " to " + to);
    }

    public InvalidStatusTransitionException(String what, String from, String to) {
        super("A " + what + " cannot move from " + from + " to " + to);
    }
}
