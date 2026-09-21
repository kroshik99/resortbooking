package com.resortapi.resortbooking.repository;

import com.resortapi.resortbooking.entity.StaffRequest;
import com.resortapi.resortbooking.entity.StaffRequestStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRequestRepository extends JpaRepository<StaffRequest, Long> {

    @EntityGraph(attributePaths = {"user"})
    Optional<StaffRequest> findByUserIdAndStatus(Long userId, StaffRequestStatus status);

    @EntityGraph(attributePaths = {"user"})
    List<StaffRequest> findAllByStatusOrderByRequestedAtAsc(StaffRequestStatus status);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Optional<StaffRequest> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Optional<StaffRequest> findFirstByUserIdOrderByRequestedAtDesc(Long userId);

    long countByStatus(StaffRequestStatus status);
}
