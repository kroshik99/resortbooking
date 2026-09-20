package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.dto.BookingDto;
import com.resortapi.resortbooking.dto.BookingForm;
import com.resortapi.resortbooking.dto.CreateBookingRequest;
import com.resortapi.resortbooking.dto.GuestDetails;
import com.resortapi.resortbooking.exception.CapacityExceededException;
import com.resortapi.resortbooking.exception.InvalidBookingDatesException;
import com.resortapi.resortbooking.exception.InvalidStatusTransitionException;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.exception.RoomNotAvailableException;
import com.resortapi.resortbooking.service.AvailabilityService;
import com.resortapi.resortbooking.service.BookingService;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class BookingPageController {

    private final BookingService bookingService;
    private final AvailabilityService availabilityService;

    public BookingPageController(BookingService bookingService, AvailabilityService availabilityService) {
        this.bookingService = bookingService;
        this.availabilityService = availabilityService;
    }

    /** Step 3: guest details, prefilled from the signed-in account. */
    @GetMapping("/book")
    public String details(@RequestParam Long typeId,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                          @RequestParam(defaultValue = "2") int guests,
                          Authentication authentication,
                          Model model,
                          RedirectAttributes flash) {

        GuestDetails profile = bookingService.profileFor(authentication.getName());

        BookingForm form = new BookingForm();
        form.setRoomTypeId(typeId);
        form.setCheckIn(checkIn);
        form.setCheckOut(checkOut);
        form.setNumGuests(guests);
        form.setFullName(profile.fullName());
        form.setEmail(profile.email());
        form.setPhone(profile.phone());

        model.addAttribute("bookingForm", form);
        try {
            model.addAttribute("quote", availabilityService.quoteFor(typeId, checkIn, checkOut));
        } catch (InvalidBookingDatesException | ResourceNotFoundException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
        return "guest/details";
    }

    /** Step 4: review. Re-renders the details page when the form is invalid. */
    @PostMapping("/book/review")
    public String review(@Valid @ModelAttribute("bookingForm") BookingForm form,
                         BindingResult result,
                         Model model) {
        model.addAttribute("quote",
                availabilityService.quoteFor(form.getRoomTypeId(), form.getCheckIn(), form.getCheckOut()));

        return result.hasErrors() ? "guest/details" : "guest/review";
    }

    /** Step 5: confirm. Always ends in a redirect, so a refresh cannot double-book. */
    @PostMapping("/bookings")
    public String create(@Valid @ModelAttribute("bookingForm") BookingForm form,
                         BindingResult result,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            model.addAttribute("quote",
                    availabilityService.quoteFor(form.getRoomTypeId(), form.getCheckIn(), form.getCheckOut()));
            return "guest/review";
        }

        try {
            BookingDto booking = bookingService.create(
                    new CreateBookingRequest(form.getRoomTypeId(), form.getCheckIn(), form.getCheckOut(),
                            form.getNumGuests(),
                            new GuestDetails(form.getFullName(), form.getEmail(), form.getPhone())),
                    Callers.of(authentication));

            flash.addFlashAttribute("success", "Booking confirmed.");
            return "redirect:/bookings/" + booking.reference();

        } catch (RoomNotAvailableException e) {
            flash.addFlashAttribute("error", "That room was just booked. Please choose another.");
            return "redirect:/rooms?" + form.searchQuery();
        } catch (CapacityExceededException | InvalidBookingDatesException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/rooms?" + form.searchQuery();
        }
    }

    @GetMapping("/bookings/{reference}")
    public String confirmation(@PathVariable String reference,
                               Authentication authentication,
                               Model model,
                               RedirectAttributes flash) {
        try {
            model.addAttribute("booking", bookingService.findByReference(reference, Callers.of(authentication)));
        } catch (ResourceNotFoundException e) {
            flash.addFlashAttribute("error", "That booking could not be found.");
            return "redirect:/me/bookings";
        }
        return "guest/confirmation";
    }

    @GetMapping("/me/bookings")
    public String myBookings(Authentication authentication, Model model) {
        model.addAttribute("bookings", bookingService.findForGuest(authentication.getName()));
        model.addAttribute("today", LocalDate.now());
        return "guest/mybookings";
    }

    /** BR-09. A POST, never a GET link, because it changes state. */
    @PostMapping("/bookings/{reference}/cancel")
    public String cancel(@PathVariable String reference,
                         Authentication authentication,
                         RedirectAttributes flash) {
        try {
            bookingService.cancel(reference, Callers.of(authentication));
            flash.addFlashAttribute("success", "Booking " + reference + " cancelled.");
        } catch (InvalidStatusTransitionException e) {
            flash.addFlashAttribute("error", "That booking can no longer be cancelled.");
        } catch (ResourceNotFoundException e) {
            flash.addFlashAttribute("error", "That booking could not be found.");
        }
        return "redirect:/me/bookings";
    }
}
