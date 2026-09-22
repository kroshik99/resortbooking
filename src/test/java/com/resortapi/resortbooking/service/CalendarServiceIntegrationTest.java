package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.RoomTypeOccupancy;
import com.resortapi.resortbooking.entity.Booking;
import com.resortapi.resortbooking.entity.Guest;
import com.resortapi.resortbooking.entity.Room;
import com.resortapi.resortbooking.entity.RoomType;
import com.resortapi.resortbooking.repository.BookingRepository;
import com.resortapi.resortbooking.repository.GuestRepository;
import com.resortapi.resortbooking.repository.RoomRepository;
import com.resortapi.resortbooking.repository.RoomTypeRepository;

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
 * occupancyNow() is deliberately separate from availability: a room can be fully
 * booked for next month and still read as "free now" until someone actually checks
 * in. These tests exist so that distinction doesn't silently erode - only manual
 * verification covered this feature otherwise.
 */
@SpringBootTest
@Transactional
class CalendarServiceIntegrationTest {

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private RoomTypeRepository roomTypes;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private GuestRepository guests;

    @Autowired
    private BookingRepository bookings;

    private RoomType standard;
    private Guest guest;

    @BeforeEach
    void setUp() {
        standard = roomTypes.findByNameIgnoreCase("Standard").orElseThrow();
        guest = guests.save(new Guest("Occupancy Test Guest", "occupancy-test@example.com", null));
    }

    @Test
    @DisplayName("a confirmed-but-not-checked-in booking does not count as occupied")
    void confirmedButNotCheckedInIsNotOccupied() {
        Room room = rooms.findFreeRooms(standard.getId(), LocalDate.now(), LocalDate.now().plusDays(2)).get(0);
        Booking booking = book(room, LocalDate.now(), LocalDate.now().plusDays(2));
        booking.confirm();
        bookings.flush();

        assertThat(occupiedStandard()).isEqualTo(0);
    }

    @Test
    @DisplayName("checking in increases the count; checking out decreases it again")
    void checkInIncreasesCheckOutDecreases() {
        int totalStandard = totalStandard();
        Room room = rooms.findFreeRooms(standard.getId(), LocalDate.now(), LocalDate.now().plusDays(2)).get(0);
        Booking booking = book(room, LocalDate.now(), LocalDate.now().plusDays(2));
        booking.confirm();
        booking.checkIn();
        bookings.flush();

        assertThat(occupiedStandard()).isEqualTo(1);
        assertThat(totalOf("Standard")).isEqualTo(totalStandard);

        booking.checkOut();
        bookings.flush();

        assertThat(occupiedStandard()).isEqualTo(0);
    }

    private int occupiedStandard() {
        return calendarService.occupancyNow().stream()
                .filter(o -> o.roomTypeName().equals("Standard"))
                .findFirst().orElseThrow().occupied();
    }

    private int totalStandard() {
        return totalOf("Standard");
    }

    private int totalOf(String roomTypeName) {
        List<RoomTypeOccupancy> occupancy = calendarService.occupancyNow();
        return occupancy.stream()
                .filter(o -> o.roomTypeName().equals(roomTypeName))
                .findFirst().orElseThrow().total();
    }

    private Booking book(Room room, LocalDate checkIn, LocalDate checkOut) {
        Booking booking = bookings.save(new Booking(
                "RB-TEST-" + System.nanoTime() % 100000,
                guest, room, checkIn, checkOut, 2, new BigDecimal("2500.00")));
        bookings.flush();
        return booking;
    }
}
