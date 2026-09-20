package com.resortapi.resortbooking.repository;

import com.resortapi.resortbooking.entity.Booking;
import com.resortapi.resortbooking.entity.Guest;
import com.resortapi.resortbooking.entity.Room;
import com.resortapi.resortbooking.entity.RoomType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-01, BR-02, BR-10 against real PostgreSQL. The overlap rule is SQL, so it can
 * only honestly be tested by the database that runs it.
 *
 * Each test rolls back, so the seed data is untouched.
 */
@SpringBootTest
@Transactional
class AvailabilityQueryIntegrationTest {

    private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);
    private static final LocalDate OCT_14 = LocalDate.of(2026, 10, 14);
    private static final LocalDate OCT_16 = LocalDate.of(2026, 10, 16);

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private RoomTypeRepository roomTypes;

    @Autowired
    private GuestRepository guests;

    @Autowired
    private BookingRepository bookings;

    private RoomType deluxe;
    private Guest guest;

    @BeforeEach
    void setUp() {
        deluxe = roomTypes.findByNameIgnoreCase("Deluxe").orElseThrow();
        guest = guests.save(new Guest("Test Guest", "overlap-test@example.com", null));
    }

    @Test
    @DisplayName("both Deluxe rooms are free when nothing is booked")
    void allRoomsFreeInitially() {
        assertThat(freeDeluxe(OCT_12, OCT_14))
                .extracting(Room::getRoomNumber)
                .containsExactly("201", "202");
    }

    @Test
    @DisplayName("BR-01: a booked room drops out for overlapping dates")
    void bookedRoomIsExcluded() {
        book(freeDeluxe(OCT_12, OCT_14).get(0), OCT_12, OCT_14);

        assertThat(freeDeluxe(OCT_12.plusDays(1), OCT_14.plusDays(1)))
                .extracting(Room::getRoomNumber)
                .containsExactly("202");
    }

    @Test
    @DisplayName("BR-02: the same room is free again on its checkout date")
    void adjacentStayIsAllowed() {
        Room room = freeDeluxe(OCT_12, OCT_14).get(0);
        book(room, OCT_12, OCT_14);

        assertThat(freeDeluxe(OCT_14, OCT_16))
                .extracting(Room::getRoomNumber)
                .contains(room.getRoomNumber());
    }

    @Test
    @DisplayName("TC-03: a cancelled booking stops blocking the dates")
    void cancelledBookingFreesTheRoom() {
        Room room = freeDeluxe(OCT_12, OCT_14).get(0);
        Booking booking = book(room, OCT_12, OCT_14);

        assertThat(freeDeluxe(OCT_12, OCT_14)).hasSize(1);

        booking.cancel();
        bookings.flush();

        assertThat(freeDeluxe(OCT_12, OCT_14)).hasSize(2);
    }

    @Test
    @DisplayName("BR-10: a room under maintenance never appears as available")
    void maintenanceRoomIsExcluded() {
        Room room = freeDeluxe(OCT_12, OCT_14).get(0);
        room.sendForMaintenance();
        rooms.flush();

        assertThat(freeDeluxe(OCT_12, OCT_14))
                .extracting(Room::getRoomNumber)
                .doesNotContain(room.getRoomNumber());
    }

    @Test
    @DisplayName("a stay wholly inside an existing one still conflicts")
    void containedStayConflicts() {
        Room room = freeDeluxe(OCT_12, OCT_16).get(0);
        book(room, OCT_12, OCT_16);

        assertThat(freeDeluxe(OCT_14, OCT_14.plusDays(1)))
                .extracting(Room::getRoomNumber)
                .doesNotContain(room.getRoomNumber());
    }

    private List<Room> freeDeluxe(LocalDate checkIn, LocalDate checkOut) {
        return rooms.findFreeRooms(deluxe.getId(), checkIn, checkOut);
    }

    private Booking book(Room room, LocalDate checkIn, LocalDate checkOut) {
        Booking booking = bookings.save(new Booking(
                "RB-TEST-" + System.nanoTime() % 100000,
                guest, room, checkIn, checkOut, 2, new BigDecimal("7000.00")));
        bookings.flush();
        return booking;
    }
}
