package com.resortapi.resortbooking.service;

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

import static org.assertj.core.api.Assertions.assertThat;

/** Counts are asserted as deltas against a captured baseline, since the dev database
 * already has real bookings from earlier sessions - the absolute numbers aren't fixed. */
@SpringBootTest
@Transactional
class DashboardServiceIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

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
        guest = guests.save(new Guest("Dashboard Test Guest", "dashboard-test@example.com", null));
    }

    @Test
    @DisplayName("a booking created now counts toward this month, regardless of its stay dates")
    void newBookingIncreasesBookingsThisMonth() {
        long before = dashboardService.today().bookingsThisMonth();

        book(freeRoomFor(LocalDate.now().plusDays(60), LocalDate.now().plusDays(62)),
                LocalDate.now().plusDays(60), LocalDate.now().plusDays(62));

        assertThat(dashboardService.today().bookingsThisMonth()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("a booking checking in today counts, and stops counting once cancelled")
    void checkInCountReflectsTodayAndExcludesCancellations() {
        long before = dashboardService.today().checkInsToday();

        Booking arrivingToday = book(
                freeRoomFor(LocalDate.now(), LocalDate.now().plusDays(2)), LocalDate.now(), LocalDate.now().plusDays(2));
        assertThat(dashboardService.today().checkInsToday()).isEqualTo(before + 1);

        arrivingToday.cancel();
        bookings.flush();
        assertThat(dashboardService.today().checkInsToday()).isEqualTo(before);
    }

    @Test
    @DisplayName("a booking checking out today counts, and stops counting once cancelled")
    void checkOutCountReflectsTodayAndExcludesCancellations() {
        long before = dashboardService.today().checkOutsToday();

        Booking leavingToday = book(
                freeRoomFor(LocalDate.now().minusDays(2), LocalDate.now()),
                LocalDate.now().minusDays(2), LocalDate.now());
        assertThat(dashboardService.today().checkOutsToday()).isEqualTo(before + 1);

        leavingToday.cancel();
        bookings.flush();
        assertThat(dashboardService.today().checkOutsToday()).isEqualTo(before);
    }

    @Test
    @DisplayName("occupiedNow/totalRooms matches CalendarService's own occupancy figures")
    void occupancyMatchesCalendarService() {
        var stats = dashboardService.today();
        long occupancyTotal = 0;
        long occupancyOccupied = 0;
        for (var o : new CalendarService(rooms, roomTypes, bookings).occupancyNow()) {
            occupancyTotal += o.total();
            occupancyOccupied += o.occupied();
        }

        assertThat(stats.totalRooms()).isEqualTo(occupancyTotal);
        assertThat(stats.occupiedNow()).isEqualTo(occupancyOccupied);
    }

    private Room freeRoomFor(LocalDate checkIn, LocalDate checkOut) {
        return rooms.findFreeRooms(standard.getId(), checkIn, checkOut).get(0);
    }

    private Booking book(Room room, LocalDate checkIn, LocalDate checkOut) {
        Booking booking = bookings.save(new Booking(
                "RB-TEST-" + System.nanoTime() % 100000,
                guest, room, checkIn, checkOut, 2, new BigDecimal("2500.00")));
        bookings.flush();
        return booking;
    }
}
