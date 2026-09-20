package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.dto.CreateRoomTypeRequest;
import com.resortapi.resortbooking.dto.RoomTypeDto;
import com.resortapi.resortbooking.service.RoomTypeService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/room-types")
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    public RoomTypeController(RoomTypeService roomTypeService) {
        this.roomTypeService = roomTypeService;
    }

    @GetMapping
    public List<RoomTypeDto> list() {
        return roomTypeService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomTypeDto create(@Valid @RequestBody CreateRoomTypeRequest request) {
        return roomTypeService.create(request);
    }

    @PutMapping("/{id}")
    public RoomTypeDto update(@PathVariable Long id, @Valid @RequestBody CreateRoomTypeRequest request) {
        return roomTypeService.update(id, request);
    }
}
