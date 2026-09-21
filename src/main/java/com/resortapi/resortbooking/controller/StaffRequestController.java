package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.dto.StaffRequestDto;
import com.resortapi.resortbooking.service.StaffRequestService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff-requests")
public class StaffRequestController {

    private final StaffRequestService staffRequestService;

    public StaffRequestController(StaffRequestService staffRequestService) {
        this.staffRequestService = staffRequestService;
    }

    /** Any authenticated (non-staff) user may ask; role-checked inside the service. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffRequestDto request(Authentication authentication) {
        return staffRequestService.request(Callers.of(authentication));
    }

    @GetMapping
    public List<StaffRequestDto> pending() {
        return staffRequestService.pending();
    }

    @PostMapping("/{id}/approve")
    public StaffRequestDto approve(@PathVariable Long id, Authentication authentication) {
        return staffRequestService.approve(id, Callers.of(authentication));
    }

    @PostMapping("/{id}/reject")
    public StaffRequestDto reject(@PathVariable Long id, Authentication authentication) {
        return staffRequestService.reject(id, Callers.of(authentication));
    }
}
