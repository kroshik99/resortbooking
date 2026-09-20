package com.resortapi.resortbooking.entity;

import com.resortapi.resortbooking.exception.InvalidStatusTransitionException;
import com.resortapi.resortbooking.service.BookingDateRules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Front desk cannot check a guest in unless today is the booking's check-in date;
 * outside that window the action becomes a request an admin must approve or reject.
 * Not part of the original spec's business rules (BR-01..BR-11) - added afterward.
 */
@Entity
@Table(name = "check_in_request")
public class CheckInRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by")
    private AppUser requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckInRequestStatus status = CheckInRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    protected CheckInRequest() {
    }

    public CheckInRequest(Booking booking, AppUser requestedBy) {
        this.booking = booking;
        this.requestedBy = requestedBy;
    }

    @PrePersist
    void onCreate() {
        requestedAt = OffsetDateTime.now(BookingDateRules.RESORT_ZONE);
    }

    public void approve(AppUser reviewer) {
        transitionTo(CheckInRequestStatus.APPROVED, reviewer);
        booking.checkIn();
    }

    public void reject(AppUser reviewer) {
        transitionTo(CheckInRequestStatus.REJECTED, reviewer);
    }

    private void transitionTo(CheckInRequestStatus next, AppUser reviewer) {
        if (status != CheckInRequestStatus.PENDING) {
            throw new InvalidStatusTransitionException(
                    "check-in request", status.name(), next.name());
        }
        status = next;
        reviewedBy = reviewer;
        reviewedAt = OffsetDateTime.now(BookingDateRules.RESORT_ZONE);
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public AppUser getRequestedBy() {
        return requestedBy;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public CheckInRequestStatus getStatus() {
        return status;
    }

    public AppUser getReviewedBy() {
        return reviewedBy;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }
}
