package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.CreateRoomTypeRequest;
import com.resortapi.resortbooking.dto.RoomTypeDto;
import com.resortapi.resortbooking.entity.RoomType;
import com.resortapi.resortbooking.exception.DuplicateResourceException;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.repository.RoomTypeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomTypeService {

    private final RoomTypeRepository roomTypes;

    public RoomTypeService(RoomTypeRepository roomTypes) {
        this.roomTypes = roomTypes;
    }

    @Transactional(readOnly = true)
    public List<RoomTypeDto> findAll() {
        return roomTypes.findAllByOrderByNameAsc().stream()
                .map(RoomTypeDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomTypeDto findById(Long id) {
        return roomTypes.findById(id)
                .map(RoomTypeDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("Room type", id));
    }

    @Transactional
    public RoomTypeDto create(CreateRoomTypeRequest request) {
        if (roomTypes.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A room type named '" + request.name() + "' already exists");
        }
        RoomType created = roomTypes.save(new RoomType(
                request.name(), request.capacity(), request.basePrice(), request.description()));
        return RoomTypeDto.from(created);
    }

    @Transactional
    public RoomTypeDto update(Long id, CreateRoomTypeRequest request) {
        RoomType roomType = roomTypes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room type", id));

        roomTypes.findByNameIgnoreCase(request.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "A room type named '" + request.name() + "' already exists");
                });

        roomType.update(request.name(), request.capacity(), request.basePrice(), request.description());
        return RoomTypeDto.from(roomType);
    }
}
