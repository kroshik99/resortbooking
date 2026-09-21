package com.resortapi.resortbooking.dto;

import com.resortapi.resortbooking.entity.StaffRequest;

import java.time.OffsetDateTime;

public record StaffRequestDto(
        Long id,
        String userEmail,
        OffsetDateTime requestedAt,
        String status,
        String reviewedByEmail,
        OffsetDateTime reviewedAt) {

    /** Must be called inside the service transaction: every association here is LAZY. */
    public static StaffRequestDto from(StaffRequest request) {
        return new StaffRequestDto(
                request.getId(),
                request.getUser().getEmail(),
                request.getRequestedAt(),
                request.getStatus().name(),
                request.getReviewedBy() == null ? null : request.getReviewedBy().getEmail(),
                request.getReviewedAt());
    }
}
