package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.service.CheckInApprovalService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes the pending-approvals count available to every page's nav bar without
 * every controller having to add it. Scoped to the page controllers only, the same
 * way PageExceptionHandler is, so it never runs for a plain REST call.
 */
@ControllerAdvice(assignableTypes = {
        HomeController.class,
        BookingPageController.class,
        StaffPageController.class,
        AdminPageController.class,
        AuthPageController.class})
public class NavModelAttributes {

    private final CheckInApprovalService checkInApprovalService;

    public NavModelAttributes(CheckInApprovalService checkInApprovalService) {
        this.checkInApprovalService = checkInApprovalService;
    }

    @ModelAttribute("pendingApprovalCount")
    public long pendingApprovalCount(Authentication authentication) {
        if (authentication == null || !isAdmin(authentication)) {
            return 0;
        }
        return checkInApprovalService.pendingCount();
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
