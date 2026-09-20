package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.exception.InvalidBookingDatesException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** BR-03, in one place so availability search and booking cannot disagree. */
public final class BookingDateRules {

    public static final ZoneId RESORT_ZONE = ZoneId.of("Asia/Manila");
    public static final int MAX_NIGHTS = 30;

    private BookingDateRules() {
    }

    public static void check(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn.isBefore(today())) {
            throw new InvalidBookingDatesException("Check-in cannot be in the past");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidBookingDatesException("Check-out must be after check-in");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > MAX_NIGHTS) {
            throw new InvalidBookingDatesException("A stay cannot be longer than " + MAX_NIGHTS + " nights");
        }
    }

    public static LocalDate today() {
        return LocalDate.now(RESORT_ZONE);
    }
}
