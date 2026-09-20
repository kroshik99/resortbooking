package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.exception.CheckInApprovalRequiredException;
import com.resortapi.resortbooking.exception.InvalidStatusTransitionException;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.service.BookingService;
import com.resortapi.resortbooking.service.CalendarService;
import com.resortapi.resortbooking.service.CheckInApprovalService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/staff")
public class StaffPageController {

    private final CalendarService calendarService;
    private final BookingService bookingService;
    private final CheckInApprovalService checkInApprovalService;

    public StaffPageController(CalendarService calendarService, BookingService bookingService,
                               CheckInApprovalService checkInApprovalService) {
        this.calendarService = calendarService;
        this.bookingService = bookingService;
        this.checkInApprovalService = checkInApprovalService;
    }

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                           Model model) {

        LocalDate weekStart = (week == null ? LocalDate.now() : week).with(DayOfWeek.MONDAY);

        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(weekStart.plusDays(i));
        }

        model.addAttribute("days", days);
        model.addAttribute("rows", calendarService.week(weekStart));
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("previousWeek", weekStart.minusWeeks(1));
        model.addAttribute("nextWeek", weekStart.plusWeeks(1));
        model.addAttribute("today", LocalDate.now());
        return "staff/calendar";
    }

    @GetMapping("/bookings/{reference}")
    public String bookingDetail(@PathVariable String reference,
                                Authentication authentication,
                                Model model,
                                RedirectAttributes flash) {
        try {
            model.addAttribute("booking", bookingService.findByReference(reference, Callers.of(authentication)));
        } catch (ResourceNotFoundException e) {
            flash.addFlashAttribute("error", "No booking with reference " + reference + ".");
            return "redirect:/staff/calendar";
        }
        checkInApprovalService.pendingFor(reference)
                .ifPresent(request -> model.addAttribute("pendingRequest", request));
        return "staff/booking-detail";
    }

    /** FR-08. Status changes are POSTs, never links. */
    @PostMapping("/bookings/{reference}/{action}")
    public String act(@PathVariable String reference,
                      @PathVariable String action,
                      Authentication authentication,
                      RedirectAttributes flash) {
        var caller = Callers.of(authentication);
        try {
            switch (action) {
                case "confirm" -> bookingService.confirm(reference);
                case "check-in" -> bookingService.checkIn(reference, caller);
                case "check-out" -> bookingService.checkOut(reference);
                case "cancel" -> bookingService.cancel(reference, caller);
                default -> throw new ResourceNotFoundException("Action", action);
            }
            flash.addFlashAttribute("success", reference + ": " + action.replace('-', ' ') + " done.");
        } catch (CheckInApprovalRequiredException e) {
            flash.addFlashAttribute("info", e.getMessage());
        } catch (InvalidStatusTransitionException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (ResourceNotFoundException e) {
            flash.addFlashAttribute("error", "No booking with reference " + reference + ".");
            return "redirect:/staff/calendar";
        }
        return "redirect:/staff/bookings/" + reference;
    }
}
