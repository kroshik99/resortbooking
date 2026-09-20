package com.resortapi.resortbooking.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-08. Pure logic: no Spring, no database, so the whole transition matrix is
 * cheap to assert exhaustively.
 */
class BookingStatusTest {

    @Test
    @DisplayName("PENDING may only become CONFIRMED or CANCELLED")
    void pendingTransitions() {
        assertAllowed(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("CONFIRMED may only become CHECKED_IN or CANCELLED")
    void confirmedTransitions() {
        assertAllowed(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("CHECKED_IN may only become CHECKED_OUT")
    void checkedInTransitions() {
        assertAllowed(BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CHECKED_OUT", "CANCELLED"})
    @DisplayName("terminal states allow nothing")
    void terminalStatesAreFinal(BookingStatus terminal) {
        assertAllowed(terminal);
    }

    @Test
    @DisplayName("a cancelled booking can never be checked in (TC-07)")
    void cancelledCannotCheckIn() {
        assertThat(BookingStatus.CANCELLED.canMoveTo(BookingStatus.CHECKED_IN)).isFalse();
    }

    @Test
    @DisplayName("no status may transition to itself")
    void noSelfTransitions() {
        for (BookingStatus status : BookingStatus.values()) {
            assertThat(status.canMoveTo(status))
                    .as("%s -> %s", status, status)
                    .isFalse();
        }
    }

    /** Asserts exactly these targets are reachable and every other one is not. */
    private void assertAllowed(BookingStatus from, BookingStatus... allowed) {
        Set<BookingStatus> permitted = allowed.length == 0
                ? EnumSet.noneOf(BookingStatus.class)
                : EnumSet.copyOf(Set.of(allowed));

        for (BookingStatus to : BookingStatus.values()) {
            assertThat(from.canMoveTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(permitted.contains(to));
        }
    }
}
