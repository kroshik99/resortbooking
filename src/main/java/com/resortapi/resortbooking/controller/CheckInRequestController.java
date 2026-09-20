package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.dto.CheckInRequestDto;
import com.resortapi.resortbooking.service.CheckInApprovalService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checkin-requests")
public class CheckInRequestController {

    private final CheckInApprovalService checkInApprovalService;

    public CheckInRequestController(CheckInApprovalService checkInApprovalService) {
        this.checkInApprovalService = checkInApprovalService;
    }

    @GetMapping
    public List<CheckInRequestDto> pending() {
        return checkInApprovalService.pending();
    }

    @PostMapping("/{id}/approve")
    public CheckInRequestDto approve(@PathVariable Long id, Authentication authentication) {
        return checkInApprovalService.approve(id, Callers.of(authentication));
    }

    @PostMapping("/{id}/reject")
    public CheckInRequestDto reject(@PathVariable Long id, Authentication authentication) {
        return checkInApprovalService.reject(id, Callers.of(authentication));
    }
}
