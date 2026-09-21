package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.exception.DuplicateResourceException;
import com.resortapi.resortbooking.exception.InvalidStatusTransitionException;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.service.StaffRequestService;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StaffRequestPageController {

    private final StaffRequestService staffRequestService;

    public StaffRequestPageController(StaffRequestService staffRequestService) {
        this.staffRequestService = staffRequestService;
    }

    @GetMapping("/me/staff-request")
    public String show(Authentication authentication, Model model) {
        model.addAttribute("latestRequest", staffRequestService.latestFor(authentication.getName()).orElse(null));
        return "guest/staff-request";
    }

    @PostMapping("/me/staff-request")
    public String request(Authentication authentication, RedirectAttributes flash) {
        try {
            staffRequestService.request(Callers.of(authentication));
            flash.addFlashAttribute("success", "Request sent. An admin will review it.");
        } catch (DuplicateResourceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/me/staff-request";
    }

    @GetMapping("/admin/staff-requests")
    public String pending(Model model) {
        model.addAttribute("requests", staffRequestService.pending());
        return "admin/staff-requests";
    }

    @PostMapping("/admin/staff-requests/{id}/approve")
    public String approve(@PathVariable Long id, Authentication authentication, RedirectAttributes flash) {
        try {
            var dto = staffRequestService.approve(id, Callers.of(authentication));
            flash.addFlashAttribute("success", dto.userEmail() + " is now front desk.");
        } catch (ResourceNotFoundException | InvalidStatusTransitionException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff-requests";
    }

    @PostMapping("/admin/staff-requests/{id}/reject")
    public String reject(@PathVariable Long id, Authentication authentication, RedirectAttributes flash) {
        try {
            var dto = staffRequestService.reject(id, Callers.of(authentication));
            flash.addFlashAttribute("success", "Request from " + dto.userEmail() + " rejected.");
        } catch (ResourceNotFoundException | InvalidStatusTransitionException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff-requests";
    }
}
