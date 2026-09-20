package com.resortapi.resortbooking.repository;

import com.resortapi.resortbooking.entity.SeasonalRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface SeasonalRateRepository extends JpaRepository<SeasonalRate, Long> {

    /** Rates touching the stay at all; the same half-open overlap test as BR-01. */
    @Query("""
            SELECT r FROM SeasonalRate r
            WHERE r.roomType.id = :roomTypeId
              AND r.startDate < :checkOut
              AND r.endDate > :checkIn
            """)
    List<SeasonalRate> findOverlapping(Long roomTypeId, LocalDate checkIn, LocalDate checkOut);
}
