package com.resortapi.resortbooking.entity;

import com.resortapi.resortbooking.exception.InvalidStatusTransitionException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    @Test
    @DisplayName("BR-02: check-out is exclusive, so Oct 12 to Oct 14 is two nights")
    void countsNightsExclusiveOfCheckOut() {
        assertThat(bookingFor(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14)).nights())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a one night stay counts as one night")
    void countsSingleNight() {
        assertThat(bookingFor(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13)).nights())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a new booking starts PENDING")
    void startsPending() {
        assertThat(newBooking().getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("the happy path walks PENDING to CHECKED_OUT")
    void walksTheHappyPath() {
        Booking booking = newBooking();

        booking.confirm();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        booking.checkIn();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);

        booking.checkOut();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_OUT);
    }

    @Test
    @DisplayName("checking in without confirming first is refused")
    void refusesCheckInFromPending() {
        assertThatThrownBy(() -> newBooking().checkIn())
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("CHECKED_IN");
    }

    @Test
    @DisplayName("TC-07: a cancelled booking cannot be checked in, and the status does not change")
    void refusesCheckInAfterCancel() {
        Booking booking = newBooking();
        booking.cancel();

        assertThatThrownBy(booking::checkIn).isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("a checked out booking cannot then be cancelled")
    void refusesCancelAfterCheckOut() {
        Booking booking = newBooking();
        booking.confirm();
        booking.checkIn();
        booking.checkOut();

        assertThatThrownBy(booking::cancel).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("rescheduling a PENDING booking moves its dates, guests and price")
    void reschedulesAPendingBooking() {
        Booking booking = newBooking();

        booking.reschedule(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), 3, new BigDecimal("14000.00"));

        assertThat(booking.getCheckIn()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(booking.getCheckOut()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(booking.getNumGuests()).isEqualTo(3);
        assertThat(booking.getTotalPrice()).isEqualByComparingTo("14000.00");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("rescheduling a CONFIRMED booking is also allowed")
    void reschedulesAConfirmedBooking() {
        Booking booking = newBooking();
        booking.confirm();

        booking.reschedule(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), 2, new BigDecimal("7000.00"));

        assertThat(booking.getCheckIn()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("a checked-in booking cannot be rescheduled")
    void refusesRescheduleAfterCheckIn() {
        Booking booking = newBooking();
        booking.confirm();
        booking.checkIn();

        assertThatThrownBy(() -> booking.reschedule(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), 2, new BigDecimal("7000.00")))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(booking.getCheckIn()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    @DisplayName("a cancelled booking cannot be rescheduled")
    void refusesRescheduleAfterCancel() {
        Booking booking = newBooking();
        booking.cancel();

        assertThatThrownBy(() -> booking.reschedule(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), 2, new BigDecimal("7000.00")))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    private static Booking newBooking() {
        return bookingFor(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14));
    }

    private static Booking bookingFor(LocalDate checkIn, LocalDate checkOut) {
        RoomType deluxe = new RoomType("Deluxe", 3, new BigDecimal("3500.00"), null);
        Room room = new Room("201", deluxe);
        Guest guest = new Guest("Juan Dela Cruz", "juan@example.com", "09171234567");

        return new Booking("RB-2026-00001", guest, room, checkIn, checkOut, 2, new BigDecimal("7000.00"));
    }
}
