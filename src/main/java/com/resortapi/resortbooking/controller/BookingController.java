package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.dto.BookingDto;
import com.resortapi.resortbooking.dto.CreateBookingRequest;
import com.resortapi.resortbooking.dto.RescheduleBookingRequest;
import com.resortapi.resortbooking.service.BookingService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto create(@Valid @RequestBody CreateBookingRequest request, Authentication authentication) {
        return bookingService.create(request, Callers.of(authentication));
    }

    @GetMapping("/{reference}")
    public BookingDto get(@PathVariable String reference, Authentication authentication) {
        return bookingService.findByReference(reference, Callers.of(authentication));
    }

    @PatchMapping("/{reference}")
    public BookingDto reschedule(@PathVariable String reference,
                                 @Valid @RequestBody RescheduleBookingRequest request,
                                 Authentication authentication) {
        return bookingService.reschedule(reference, request.checkIn(), request.checkOut(), request.numGuests(),
                Callers.of(authentication));
    }

    @PostMapping("/{reference}/cancel")
    public BookingDto cancel(@PathVariable String reference, Authentication authentication) {
        return bookingService.cancel(reference, Callers.of(authentication));
    }

    @PostMapping("/{reference}/confirm")
    public BookingDto confirm(@PathVariable String reference) {
        return bookingService.confirm(reference);
    }

    @PostMapping("/{reference}/check-in")
    public BookingDto checkIn(@PathVariable String reference, Authentication authentication) {
        return bookingService.checkIn(reference, Callers.of(authentication));
    }

    @PostMapping("/{reference}/check-out")
    public BookingDto checkOut(@PathVariable String reference) {
        return bookingService.checkOut(reference);
    }
}
