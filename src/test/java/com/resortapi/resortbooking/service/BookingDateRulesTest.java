package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.exception.InvalidBookingDatesException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BR-03: check-in today or later, check-out after check-in, at most 30 nights. */
class BookingDateRulesTest {

    private static final LocalDate TODAY = BookingDateRules.today();

    @Test
    @DisplayName("a normal future stay is accepted")
    void acceptsFutureStay() {
        assertThatCode(() -> BookingDateRules.check(TODAY.plusDays(1), TODAY.plusDays(3)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checking in today is allowed, not just future dates")
    void acceptsToday() {
        assertThatCode(() -> BookingDateRules.check(TODAY, TODAY.plusDays(1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a past check-in is rejected")
    void rejectsPastCheckIn() {
        assertThatThrownBy(() -> BookingDateRules.check(TODAY.minusDays(1), TODAY.plusDays(2)))
                .isInstanceOf(InvalidBookingDatesException.class)
                .hasMessageContaining("past");
    }

    @Test
    @DisplayName("check-out equal to check-in is rejected: a stay is at least one night")
    void rejectsZeroNightStay() {
        assertThatThrownBy(() -> BookingDateRules.check(TODAY.plusDays(1), TODAY.plusDays(1)))
                .isInstanceOf(InvalidBookingDatesException.class)
                .hasMessageContaining("after check-in");
    }

    @Test
    @DisplayName("check-out before check-in is rejected (TC-08)")
    void rejectsReversedDates() {
        assertThatThrownBy(() -> BookingDateRules.check(TODAY.plusDays(5), TODAY.plusDays(2)))
                .isInstanceOf(InvalidBookingDatesException.class);
    }

    @Test
    @DisplayName("exactly 30 nights is allowed, 31 is not")
    void enforcesMaximumStayAtTheBoundary() {
        assertThatCode(() -> BookingDateRules.check(TODAY.plusDays(1), TODAY.plusDays(31)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> BookingDateRules.check(TODAY.plusDays(1), TODAY.plusDays(32)))
                .isInstanceOf(InvalidBookingDatesException.class)
                .hasMessageContaining("30 nights");
    }
}
