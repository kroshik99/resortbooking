package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.CreateRoomRequest;
import com.resortapi.resortbooking.dto.RoomDto;
import com.resortapi.resortbooking.entity.Room;
import com.resortapi.resortbooking.entity.RoomStatus;
import com.resortapi.resortbooking.entity.RoomType;
import com.resortapi.resortbooking.exception.DuplicateResourceException;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.repository.RoomRepository;
import com.resortapi.resortbooking.repository.RoomTypeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository rooms;
    private final RoomTypeRepository roomTypes;

    public RoomService(RoomRepository rooms, RoomTypeRepository roomTypes) {
        this.rooms = rooms;
        this.roomTypes = roomTypes;
    }

    @Transactional(readOnly = true)
    public List<RoomDto> findAll() {
        return rooms.findAllByOrderByRoomNumberAsc().stream()
                .map(RoomDto::from)
                .toList();
    }

    @Transactional
    public RoomDto create(CreateRoomRequest request) {
        if (rooms.existsByRoomNumber(request.roomNumber())) {
            throw new DuplicateResourceException("Room " + request.roomNumber() + " already exists");
        }
        RoomType roomType = roomTypes.findById(request.roomTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Room type", request.roomTypeId()));

        return RoomDto.from(rooms.save(new Room(request.roomNumber(), roomType)));
    }

    /** FR-14 / BR-10: a room in maintenance drops out of availability. */
    @Transactional
    public RoomDto updateStatus(Long id, RoomStatus status) {
        Room room = rooms.findWithRoomTypeById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", id));

        switch (status) {
            case AVAILABLE -> room.returnToService();
            case MAINTENANCE -> room.sendForMaintenance();
        }
        return RoomDto.from(room);
    }
}
