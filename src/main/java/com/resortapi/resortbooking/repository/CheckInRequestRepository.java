package com.resortapi.resortbooking.repository;

import com.resortapi.resortbooking.entity.CheckInRequest;
import com.resortapi.resortbooking.entity.CheckInRequestStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckInRequestRepository extends JpaRepository<CheckInRequest, Long> {

    @EntityGraph(attributePaths = {"booking", "booking.room", "booking.room.roomType", "booking.guest", "requestedBy"})
    Optional<CheckInRequest> findByBookingIdAndStatus(Long bookingId, CheckInRequestStatus status);

    @EntityGraph(attributePaths = {"booking", "booking.room", "booking.room.roomType", "booking.guest", "requestedBy"})
    Optional<CheckInRequest> findByBookingReferenceAndStatus(String bookingReference, CheckInRequestStatus status);

    @EntityGraph(attributePaths = {"booking", "booking.room", "booking.room.roomType", "booking.guest", "requestedBy"})
    List<CheckInRequest> findAllByStatusOrderByRequestedAtAsc(CheckInRequestStatus status);

    @EntityGraph(attributePaths = {"booking", "requestedBy", "reviewedBy"})
    Optional<CheckInRequest> findWithDetailsById(Long id);

    long countByStatus(CheckInRequestStatus status);

    void deleteByBookingId(Long bookingId);
}
