package com.resortapi.resortbooking.exception;

/**
 * Front desk tried to check in a booking whose check-in date isn't today. The
 * attempt itself files (or finds an existing) request for an admin to review.
 */
public class CheckInApprovalRequiredException extends RuntimeException {

    public CheckInApprovalRequiredException(String message) {
        super(message);
    }
}
