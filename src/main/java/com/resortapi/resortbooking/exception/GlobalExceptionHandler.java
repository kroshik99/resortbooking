package com.resortapi.resortbooking.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON problem responses for the API only. Restricted to @RestController so that a
 * page controller's exception is not answered with JSON in a browser.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Login failed", e.getMessage(), "BAD_CREDENTIALS");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Not allowed",
                "Your role cannot perform this action.", "ACCESS_DENIED");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", e.getMessage(), "RESOURCE_NOT_FOUND");
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException e) {
        return problem(HttpStatus.CONFLICT, "Already exists", e.getMessage(), "DUPLICATE_RESOURCE");
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail handleInvalidTransition(InvalidStatusTransitionException e) {
        return problem(HttpStatus.CONFLICT, "Invalid status change", e.getMessage(), "INVALID_STATUS_TRANSITION");
    }

    @ExceptionHandler(RoomNotAvailableException.class)
    public ProblemDetail handleRoomNotAvailable(RoomNotAvailableException e) {
        return problem(HttpStatus.CONFLICT, "Room not available", e.getMessage(), "ROOM_NOT_AVAILABLE");
    }

    @ExceptionHandler(CapacityExceededException.class)
    public ProblemDetail handleCapacity(CapacityExceededException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Too many guests", e.getMessage(), "CAPACITY_EXCEEDED");
    }

    @ExceptionHandler(InvalidBookingDatesException.class)
    public ProblemDetail handleDates(InvalidBookingDatesException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid dates", e.getMessage(), "INVALID_DATES");
    }

    @ExceptionHandler(CheckInApprovalRequiredException.class)
    public ProblemDetail handleApprovalRequired(CheckInApprovalRequiredException e) {
        return problem(HttpStatus.CONFLICT, "Approval required", e.getMessage(), "APPROVAL_REQUIRED");
    }

    /** Safety net: any constraint a concurrent request tripped is a conflict, not a server fault. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "That change conflicts with data saved by another request. Please try again.",
                "DATA_CONFLICT");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", "VALIDATION_FAILED");

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(),
                    fieldError.getDefaultMessage() == null ? "is invalid" : fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
