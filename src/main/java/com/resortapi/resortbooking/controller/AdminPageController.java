package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.exception.InvalidStatusTransitionException;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.service.CheckInApprovalService;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/checkin-requests")
public class AdminPageController {

    private final CheckInApprovalService checkInApprovalService;

    public AdminPageController(CheckInApprovalService checkInApprovalService) {
        this.checkInApprovalService = checkInApprovalService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("requests", checkInApprovalService.pending());
        return "admin/checkin-requests";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, Authentication authentication, RedirectAttributes flash) {
        try {
            var dto = checkInApprovalService.approve(id, Callers.of(authentication));
            flash.addFlashAttribute("success", dto.bookingReference() + " checked in.");
        } catch (ResourceNotFoundException | InvalidStatusTransitionException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/checkin-requests";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, Authentication authentication, RedirectAttributes flash) {
        try {
            var dto = checkInApprovalService.reject(id, Callers.of(authentication));
            flash.addFlashAttribute("success", dto.bookingReference() + " check-in request rejected.");
        } catch (ResourceNotFoundException | InvalidStatusTransitionException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/checkin-requests";
    }
}
