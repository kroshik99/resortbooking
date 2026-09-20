package com.resortapi.resortbooking.repository;

import com.resortapi.resortbooking.entity.Room;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * BR-01 + BR-10 as JPQL. Declared once so the browsing query and the booking
     * query can never drift apart; only the lock differs.
     */
    String FREE_ROOMS = """
            SELECT r FROM Room r
            WHERE r.roomType.id = :typeId
              AND r.status = com.resortapi.resortbooking.entity.RoomStatus.AVAILABLE
              AND NOT EXISTS (
                SELECT b FROM Booking b
                WHERE b.room = r
                  AND b.status <> com.resortapi.resortbooking.entity.BookingStatus.CANCELLED
                  AND b.checkIn < :checkOut
                  AND b.checkOut > :checkIn)
            ORDER BY r.roomNumber
            """;

    /** Browsing: no lock, nothing is being reserved yet. */
    @Query(FREE_ROOMS)
    List<Room> findFreeRooms(Long typeId, LocalDate checkIn, LocalDate checkOut);

    /** Booking: SELECT ... FOR UPDATE so two requests cannot claim the same room. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(FREE_ROOMS)
    List<Room> findFreeRoomsForUpdate(Long typeId, LocalDate checkIn, LocalDate checkOut);

    boolean existsByRoomNumber(String roomNumber);

    /** roomType is LAZY, so fetch it up front rather than one query per row. */
    @EntityGraph(attributePaths = "roomType")
    List<Room> findAllByOrderByRoomNumberAsc();

    @EntityGraph(attributePaths = "roomType")
    Optional<Room> findWithRoomTypeById(Long id);
}
