package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.dto.CreateRoomRequest;
import com.resortapi.resortbooking.dto.RoomDto;
import com.resortapi.resortbooking.dto.UpdateRoomStatusRequest;
import com.resortapi.resortbooking.service.RoomService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomDto> list() {
        return roomService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomDto create(@Valid @RequestBody CreateRoomRequest request) {
        return roomService.create(request);
    }

    @PatchMapping("/{id}/status")
    public RoomDto updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateRoomStatusRequest request) {
        return roomService.updateStatus(id, request.status());
    }
}
