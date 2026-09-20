package com.resortapi.resortbooking.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String role) {
}
