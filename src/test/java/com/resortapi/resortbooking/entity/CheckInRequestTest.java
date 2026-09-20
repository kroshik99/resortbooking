package com.resortapi.resortbooking.entity;

import com.resortapi.resortbooking.exception.InvalidStatusTransitionException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckInRequestTest {

    @Test
    @DisplayName("a new request starts PENDING")
    void startsPending() {
        assertThat(newRequest().getStatus()).isEqualTo(CheckInRequestStatus.PENDING);
    }

    @Test
    @DisplayName("approving a request also checks the underlying booking in")
    void approveAlsoChecksInTheBooking() {
        Booking booking = confirmedBooking();
        CheckInRequest request = new CheckInRequest(booking, staffUser());

        request.approve(adminUser());

        assertThat(request.getStatus()).isEqualTo(CheckInRequestStatus.APPROVED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
    }

    @Test
    @DisplayName("rejecting a request leaves the booking untouched")
    void rejectDoesNotTouchTheBooking() {
        Booking booking = confirmedBooking();
        CheckInRequest request = new CheckInRequest(booking, staffUser());

        request.reject(adminUser());

        assertThat(request.getStatus()).isEqualTo(CheckInRequestStatus.REJECTED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("an already-approved request cannot be approved again")
    void cannotApproveTwice() {
        CheckInRequest request = new CheckInRequest(confirmedBooking(), staffUser());
        request.approve(adminUser());

        assertThatThrownBy(() -> request.approve(adminUser()))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("a rejected request cannot later be approved")
    void cannotApproveAfterReject() {
        CheckInRequest request = new CheckInRequest(confirmedBooking(), staffUser());
        request.reject(adminUser());

        assertThatThrownBy(() -> request.approve(adminUser()))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    private static CheckInRequest newRequest() {
        return new CheckInRequest(confirmedBooking(), staffUser());
    }

    private static Booking confirmedBooking() {
        RoomType standard = new RoomType("Standard", 2, new BigDecimal("2500.00"), null);
        Room room = new Room("101", standard);
        Guest guest = new Guest("Juan Dela Cruz", "juan@example.com", null);
        Booking booking = new Booking("RB-2026-00001", guest, room,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), 2, new BigDecimal("5000.00"));
        booking.confirm();
        return booking;
    }

    private static AppUser staffUser() {
        return new AppUser("frontdesk@resort.test", "hash", Role.FRONT_DESK);
    }

    private static AppUser adminUser() {
        return new AppUser("admin@resort.test", "hash", Role.ADMIN);
    }
}
