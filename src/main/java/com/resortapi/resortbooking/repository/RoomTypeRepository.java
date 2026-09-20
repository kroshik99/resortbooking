package com.resortapi.resortbooking.repository;

import com.resortapi.resortbooking.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<RoomType> findByNameIgnoreCase(String name);

    List<RoomType> findAllByOrderByNameAsc();
}
