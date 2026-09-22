package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.DashboardStats;
import com.resortapi.resortbooking.entity.BookingStatus;
import com.resortapi.resortbooking.repository.BookingRepository;
import com.resortapi.resortbooking.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Front-desk activity at a glance - separate from booking availability, which never changes for this. */
@Service
public class DashboardService {

    private final BookingRepository bookings;
    private final RoomRepository rooms;

    public DashboardService(BookingRepository bookings, RoomRepository rooms) {
        this.bookings = bookings;
        this.rooms = rooms;
    }

    @Transactional(readOnly = true)
    public DashboardStats today() {
        LocalDate today = BookingDateRules.today();
        LocalDate monthStart = today.withDayOfMonth(1);
        OffsetDateTime monthStartAt = monthStart.atStartOfDay(BookingDateRules.RESORT_ZONE).toOffsetDateTime();
        OffsetDateTime monthEndAt = monthStart.plusMonths(1).atStartOfDay(BookingDateRules.RESORT_ZONE).toOffsetDateTime();

        return new DashboardStats(
                bookings.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(monthStartAt, monthEndAt),
                bookings.countByCheckInDateExcludingStatus(today, BookingStatus.CANCELLED),
                bookings.countByCheckOutDateExcludingStatus(today, BookingStatus.CANCELLED),
                bookings.findCheckedInRoomIds().size(),
                rooms.count());
    }
}
