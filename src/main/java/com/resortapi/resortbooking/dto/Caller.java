package com.resortapi.resortbooking.dto;

/**
 * Who is making the request, in terms the service layer cares about. Keeps Spring
 * Security types out of the services and makes them testable without a SecurityContext.
 */
public record Caller(String email, boolean staff, boolean admin) {
}
