package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.dto.BookingDto;
import com.resortapi.resortbooking.dto.RescheduleBookingForm;
import com.resortapi.resortbooking.exception.CapacityExceededException;
import com.resortapi.resortbooking.exception.CheckInApprovalRequiredException;
import com.resortapi.resortbooking.exception.InvalidBookingDatesException;
import com.resortapi.resortbooking.exception.InvalidStatusTransitionException;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.exception.RoomNotAvailableException;
import com.resortapi.resortbooking.service.BookingService;
import com.resortapi.resortbooking.service.CalendarService;
import com.resortapi.resortbooking.service.CheckInApprovalService;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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

    /** FR-09: search by reference or guest name; blank query shows no results. */
    @GetMapping("/bookings")
    public String search(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("query", q);
        model.addAttribute("results", (q == null || q.isBlank()) ? List.of() : bookingService.search(q));
        return "staff/bookings";
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

    @GetMapping("/bookings/{reference}/edit")
    public String editForm(@PathVariable String reference,
                           Authentication authentication,
                           Model model,
                           RedirectAttributes flash) {
        BookingDto booking;
        try {
            booking = bookingService.findByReference(reference, Callers.of(authentication));
        } catch (ResourceNotFoundException e) {
            flash.addFlashAttribute("error", "No booking with reference " + reference + ".");
            return "redirect:/staff/calendar";
        }
        if (!model.containsAttribute("rescheduleForm")) {
            RescheduleBookingForm form = new RescheduleBookingForm();
            form.setCheckIn(booking.checkIn());
            form.setCheckOut(booking.checkOut());
            form.setNumGuests(booking.numGuests());
            model.addAttribute("rescheduleForm", form);
        }
        model.addAttribute("booking", booking);
        return "staff/booking-edit";
    }

    /** Moves dates/party size on the booking's own room; changing the room is cancel-and-rebook. */
    @PostMapping("/bookings/{reference}/edit")
    public String edit(@PathVariable String reference,
                       @Valid @ModelAttribute("rescheduleForm") RescheduleBookingForm form,
                       BindingResult result,
                       Authentication authentication,
                       Model model,
                       RedirectAttributes flash) {
        var caller = Callers.of(authentication);
        if (!result.hasErrors()) {
            try {
                bookingService.reschedule(
                        reference, form.getCheckIn(), form.getCheckOut(), form.getNumGuests(), caller);
                flash.addFlashAttribute("success", reference + " updated.");
                return "redirect:/staff/bookings/" + reference;
            } catch (InvalidBookingDatesException | CapacityExceededException e) {
                model.addAttribute("error", e.getMessage());
            } catch (RoomNotAvailableException e) {
                model.addAttribute("error", "This room is not free for those dates.");
            } catch (ResourceNotFoundException e) {
                flash.addFlashAttribute("error", "No booking with reference " + reference + ".");
                return "redirect:/staff/calendar";
            }
        }
        model.addAttribute("booking", bookingService.findByReference(reference, caller));
        return "staff/booking-edit";
    }
}
