package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.service.CheckInApprovalService;
import com.resortapi.resortbooking.service.StaffRequestService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes the pending-approvals counts available to every page's nav bar without
 * every controller having to add it. Scoped to the page controllers only, the same
 * way PageExceptionHandler is, so it never runs for a plain REST call.
 */
@ControllerAdvice(assignableTypes = {
        HomeController.class,
        BookingPageController.class,
        StaffPageController.class,
        AdminPageController.class,
        AdminCatalogPageController.class,
        AuthPageController.class,
        StaffRequestPageController.class})
public class NavModelAttributes {

    private final CheckInApprovalService checkInApprovalService;
    private final StaffRequestService staffRequestService;

    public NavModelAttributes(CheckInApprovalService checkInApprovalService,
                              StaffRequestService staffRequestService) {
        this.checkInApprovalService = checkInApprovalService;
        this.staffRequestService = staffRequestService;
    }

    @ModelAttribute("pendingApprovalCount")
    public long pendingApprovalCount(Authentication authentication) {
        if (authentication == null || !isAdmin(authentication)) {
            return 0;
        }
        return checkInApprovalService.pendingCount();
    }

    @ModelAttribute("pendingStaffRequestCount")
    public long pendingStaffRequestCount(Authentication authentication) {
        if (authentication == null || !isAdmin(authentication)) {
            return 0;
        }
        return staffRequestService.pendingCount();
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
