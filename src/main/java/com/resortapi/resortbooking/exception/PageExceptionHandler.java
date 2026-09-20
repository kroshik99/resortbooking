package com.resortapi.resortbooking.exception;

import com.resortapi.resortbooking.controller.AuthPageController;
import com.resortapi.resortbooking.controller.BookingPageController;
import com.resortapi.resortbooking.controller.HomeController;
import com.resortapi.resortbooking.controller.StaffPageController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Browser counterpart to {@link GlobalExceptionHandler}: a person gets a page with a
 * message, not a JSON problem document and not a stack trace.
 *
 * Bound to the page controllers by type so it can never intercept an API response.
 */
@ControllerAdvice(assignableTypes = {
        HomeController.class,
        BookingPageController.class,
        StaffPageController.class,
        AuthPageController.class})
public class PageExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PageExceptionHandler.class);

    @ExceptionHandler({InvalidBookingDatesException.class, CapacityExceededException.class})
    public String handleBadInput(RuntimeException e, RedirectAttributes flash) {
        flash.addFlashAttribute("error", e.getMessage());
        return "redirect:/";
    }

    @ExceptionHandler(RoomNotAvailableException.class)
    public String handleUnavailable(RoomNotAvailableException e, RedirectAttributes flash) {
        flash.addFlashAttribute("error", "That room was just booked. Please choose another.");
        return "redirect:/";
    }

    @ExceptionHandler({ResourceNotFoundException.class, InvalidStatusTransitionException.class})
    public String handleNotFoundOrBadTransition(RuntimeException e, RedirectAttributes flash) {
        log.debug("Page request failed: {}", e.getMessage());
        flash.addFlashAttribute("error", "We could not complete that. Please start again.");
        return "redirect:/";
    }
}
