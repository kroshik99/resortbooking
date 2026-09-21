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
 * Self-service escalation from GUEST to FRONT_DESK: a guest asks, an admin decides.
 * Escalation to ADMIN stays out of this entirely on purpose - it is still a direct
 * database action, never something a request can win.
 */
@Entity
@Table(name = "staff_request")
public class StaffRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffRequestStatus status = StaffRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    protected StaffRequest() {
    }

    public StaffRequest(AppUser user) {
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        requestedAt = OffsetDateTime.now(BookingDateRules.RESORT_ZONE);
    }

    public void approve(AppUser reviewer) {
        transitionTo(StaffRequestStatus.APPROVED, reviewer);
        user.promoteTo(Role.FRONT_DESK);
    }

    public void reject(AppUser reviewer) {
        transitionTo(StaffRequestStatus.REJECTED, reviewer);
    }

    private void transitionTo(StaffRequestStatus next, AppUser reviewer) {
        if (status != StaffRequestStatus.PENDING) {
            throw new InvalidStatusTransitionException("staff request", status.name(), next.name());
        }
        status = next;
        reviewedBy = reviewer;
        reviewedAt = OffsetDateTime.now(BookingDateRules.RESORT_ZONE);
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public StaffRequestStatus getStatus() {
        return status;
    }

    public AppUser getReviewedBy() {
        return reviewedBy;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }
}
