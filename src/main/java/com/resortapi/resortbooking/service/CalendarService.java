package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.CalendarBar;
import com.resortapi.resortbooking.dto.CalendarRow;
import com.resortapi.resortbooking.dto.RoomTypeOccupancy;
import com.resortapi.resortbooking.entity.Booking;
import com.resortapi.resortbooking.entity.Room;
import com.resortapi.resortbooking.entity.RoomType;
import com.resortapi.resortbooking.repository.BookingRepository;
import com.resortapi.resortbooking.repository.RoomRepository;
import com.resortapi.resortbooking.repository.RoomTypeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CalendarService {

    private static final int DAYS_SHOWN = 7;
    /** Column 1 holds the room label, so day one starts at column 2. */
    private static final int FIRST_DAY_COLUMN = 2;

    private final RoomRepository rooms;
    private final RoomTypeRepository roomTypes;
    private final BookingRepository bookings;

    public CalendarService(RoomRepository rooms, RoomTypeRepository roomTypes, BookingRepository bookings) {
        this.rooms = rooms;
        this.roomTypes = roomTypes;
        this.bookings = bookings;
    }

    /** FR-06: every room for one week, with each booking placed on the grid. */
    @Transactional(readOnly = true)
    public List<CalendarRow> week(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(DAYS_SHOWN);

        Map<Long, List<Booking>> byRoom = bookings.findForCalendar(weekStart, weekEnd).stream()
                .collect(Collectors.groupingBy(booking -> booking.getRoom().getId()));

        List<CalendarRow> rows = new ArrayList<>();
        for (Room room : rooms.findAllByOrderByRoomNumberAsc()) {
            List<CalendarBar> bars = byRoom.getOrDefault(room.getId(), List.of()).stream()
                    .map(booking -> toBar(booking, weekStart, weekEnd))
                    .toList();

            rows.add(new CalendarRow(
                    room.getRoomNumber(), room.getRoomType().getName(), room.getStatus().name(), bars));
        }
        return rows;
    }

    /**
     * Rooms physically occupied right now, per room type - distinct from "booked",
     * which includes stays that haven't started yet. Availability search still keys
     * off every non-cancelled booking regardless of check-in status (that's what
     * actually prevents double-booking); this is a separate, purely informational
     * view for staff deciding whether a walk-in can be seated right now.
     */
    @Transactional(readOnly = true)
    public List<RoomTypeOccupancy> occupancyNow() {
        Set<Long> checkedInRoomIds = new HashSet<>(bookings.findCheckedInRoomIds());

        Map<Long, int[]> counts = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (RoomType roomType : roomTypes.findAllByOrderByNameAsc()) {
            counts.put(roomType.getId(), new int[2]);
            names.put(roomType.getId(), roomType.getName());
        }

        for (Room room : rooms.findAllByOrderByRoomNumberAsc()) {
            int[] count = counts.get(room.getRoomType().getId());
            count[1]++;
            if (checkedInRoomIds.contains(room.getId())) {
                count[0]++;
            }
        }

        return counts.entrySet().stream()
                .map(entry -> new RoomTypeOccupancy(names.get(entry.getKey()), entry.getValue()[0], entry.getValue()[1]))
                .toList();
    }

    /** Clips a stay to the visible week, so a booking spanning the edge still draws correctly. */
    private CalendarBar toBar(Booking booking, LocalDate weekStart, LocalDate weekEnd) {
        LocalDate from = booking.getCheckIn().isBefore(weekStart) ? weekStart : booking.getCheckIn();
        LocalDate to = booking.getCheckOut().isAfter(weekEnd) ? weekEnd : booking.getCheckOut();

        int startCol = FIRST_DAY_COLUMN + (int) ChronoUnit.DAYS.between(weekStart, from);
        int span = Math.max(1, (int) ChronoUnit.DAYS.between(from, to));

        return new CalendarBar(
                booking.getReference(),
                booking.getGuest().getFullName(),
                booking.getStatus().name(),
                startCol,
                span);
    }
}
