package com.resortapi.resortbooking.entity;

import com.resortapi.resortbooking.exception.InvalidStatusTransitionException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffRequestTest {

    @Test
    @DisplayName("a new request starts PENDING")
    void startsPending() {
        assertThat(newRequest().getStatus()).isEqualTo(StaffRequestStatus.PENDING);
    }

    @Test
    @DisplayName("approving a request promotes the user to FRONT_DESK")
    void approvePromotesTheUser() {
        AppUser guest = guestUser();
        StaffRequest request = new StaffRequest(guest);

        request.approve(adminUser());

        assertThat(request.getStatus()).isEqualTo(StaffRequestStatus.APPROVED);
        assertThat(guest.getRole()).isEqualTo(Role.FRONT_DESK);
    }

    @Test
    @DisplayName("rejecting a request leaves the user's role untouched")
    void rejectDoesNotTouchTheRole() {
        AppUser guest = guestUser();
        StaffRequest request = new StaffRequest(guest);

        request.reject(adminUser());

        assertThat(request.getStatus()).isEqualTo(StaffRequestStatus.REJECTED);
        assertThat(guest.getRole()).isEqualTo(Role.GUEST);
    }

    @Test
    @DisplayName("an already-approved request cannot be approved again")
    void cannotApproveTwice() {
        StaffRequest request = new StaffRequest(guestUser());
        request.approve(adminUser());

        assertThatThrownBy(() -> request.approve(adminUser()))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("a rejected request cannot later be approved")
    void cannotApproveAfterReject() {
        StaffRequest request = new StaffRequest(guestUser());
        request.reject(adminUser());

        assertThatThrownBy(() -> request.approve(adminUser()))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    private static StaffRequest newRequest() {
        return new StaffRequest(guestUser());
    }

    private static AppUser guestUser() {
        return new AppUser("guest@example.com", "hash", Role.GUEST);
    }

    private static AppUser adminUser() {
        return new AppUser("admin@resort.test", "hash", Role.ADMIN);
    }
}
