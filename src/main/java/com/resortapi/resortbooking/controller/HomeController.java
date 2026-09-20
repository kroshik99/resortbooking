package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.exception.InvalidBookingDatesException;
import com.resortapi.resortbooking.service.AvailabilityService;
import com.resortapi.resortbooking.service.BookingDateRules;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Controller
public class HomeController {

    private final AvailabilityService availabilityService;

    public HomeController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/")
    public String search(Model model) {
        LocalDate today = BookingDateRules.today();
        model.addAttribute("defaultCheckIn", today.plusDays(1));
        model.addAttribute("defaultCheckOut", today.plusDays(3));
        return "guest/search";
    }

    @GetMapping("/rooms")
    public String rooms(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                        @RequestParam(defaultValue = "2") int guests,
                        Model model,
                        RedirectAttributes flash) {
        try {
            model.addAttribute("options", availabilityService.search(checkIn, checkOut, guests, null));
        } catch (InvalidBookingDatesException e) {
            // Pages get a flash message and go back to the form, not a JSON problem document.
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }

        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("guests", guests);
        model.addAttribute("nights", ChronoUnit.DAYS.between(checkIn, checkOut));
        return "guest/rooms";
    }
}
