package com.resortapi.resortbooking.repository;

import com.resortapi.resortbooking.entity.Booking;
import com.resortapi.resortbooking.entity.BookingStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"room", "room.roomType", "guest"})
    Optional<Booking> findByReference(String reference);

    /** FR-04. One query with the associations fetched, so the list has no N+1. */
    @EntityGraph(attributePaths = {"room", "room.roomType", "guest"})
    List<Booking> findByGuestEmailIgnoreCaseOrderByCheckInDesc(String email);

    /** FR-06: one query for the whole week, with associations fetched to avoid N+1. */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.room r JOIN FETCH r.roomType JOIN FETCH b.guest
            WHERE b.status <> com.resortapi.resortbooking.entity.BookingStatus.CANCELLED
              AND b.checkIn < :weekEnd AND b.checkOut > :weekStart
            ORDER BY r.roomNumber, b.checkIn
            """)
    List<Booking> findForCalendar(LocalDate weekStart, LocalDate weekEnd);

    /** FR-09: staff search by reference or guest name. */
    @EntityGraph(attributePaths = {"room", "room.roomType", "guest"})
    List<Booking> findTop50ByReferenceContainingIgnoreCaseOrGuestFullNameContainingIgnoreCaseOrderByCheckInDesc(
            String reference, String guestName);

    /** BR-11: references come from a sequence, so they never expose a row id. */
    @Query(value = "SELECT nextval('booking_ref_seq')", nativeQuery = true)
    long nextReferenceNumber();

    /** For editing: is this room already taken by a different booking on these dates? */
    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.room.id = :roomId
              AND b.id <> :excludeBookingId
              AND b.status <> com.resortapi.resortbooking.entity.BookingStatus.CANCELLED
              AND b.checkIn < :checkOut AND b.checkOut > :checkIn
            """)
    boolean existsOverlapping(Long roomId, LocalDate checkIn, LocalDate checkOut, Long excludeBookingId);

    /** For sending a room to maintenance: does it still owe someone a stay? */
    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.room.id = :roomId
              AND b.status IN (com.resortapi.resortbooking.entity.BookingStatus.PENDING,
                                com.resortapi.resortbooking.entity.BookingStatus.CONFIRMED,
                                com.resortapi.resortbooking.entity.BookingStatus.CHECKED_IN)
              AND b.checkOut > :today
            """)
    boolean existsActiveBookingAfter(Long roomId, LocalDate today);

    /** Rooms physically occupied right now - distinct from "booked", which includes stays that haven't
     * started yet. A room only enters this set through an actual check-in. */
    @Query("""
            SELECT b.room.id FROM Booking b
            WHERE b.status = com.resortapi.resortbooking.entity.BookingStatus.CHECKED_IN
            """)
    List<Long> findCheckedInRoomIds();

    /** Dashboard: booking activity, not occupancy - counts every reservation made this month. */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(OffsetDateTime monthStart, OffsetDateTime monthEnd);

    /**
     * Dashboard: arrivals/departures expected today, cancellations excluded. Written as
     * @Query rather than derived naming - "checkIn" ending in "In" is misparsed as the
     * SQL IN operator by Spring Data's method-name parser (countByCheckInAnd... resolves
     * to a non-existent "check" property).
     */
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.checkIn = :date AND b.status <> :excluded")
    long countByCheckInDateExcludingStatus(LocalDate date, BookingStatus excluded);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.checkOut = :date AND b.status <> :excluded")
    long countByCheckOutDateExcludingStatus(LocalDate date, BookingStatus excluded);
}
