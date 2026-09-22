package com.resortapi.resortbooking.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fills a gap noted in code_review.md (H-1): the /admin and /staff page controllers
 * (AdminCatalogPageController, StaffPageController's newer routes) had no test proving
 * their role gating actually works, only manual curl verification during development.
 *
 * Builds MockMvc explicitly with the Spring Security test configurer rather than
 * relying on @AutoConfigureMockMvc to wire it in - on this project's Spring Boot 4 /
 * Spring Security 7 combination that auto-wiring did not apply, and @WithMockUser
 * requests were silently treated as anonymous (redirected to /login instead of
 * honouring the mocked role).
 */
@SpringBootTest
class AdminPageSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("an anonymous visitor is redirected to login, not shown the admin page")
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/admin/rooms")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/staff/calendar")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "GUEST")
    @DisplayName("a GUEST is forbidden from every /admin and /staff page")
    void guestCannotReachAdminOrStaffPages() throws Exception {
        mvc.perform(get("/admin/rooms")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/room-types")).andExpect(status().isForbidden());
        mvc.perform(get("/staff/calendar")).andExpect(status().isForbidden());
        mvc.perform(get("/staff/bookings")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FRONT_DESK")
    @DisplayName("FRONT_DESK reaches staff pages but not /admin pages")
    void frontDeskReachesStaffButNotAdmin() throws Exception {
        mvc.perform(get("/staff/calendar")).andExpect(status().isOk());
        mvc.perform(get("/staff/bookings")).andExpect(status().isOk());
        mvc.perform(get("/admin/rooms")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/room-types")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/checkin-requests")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/staff-requests")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN reaches every /admin and /staff page")
    void adminReachesEverything() throws Exception {
        mvc.perform(get("/staff/calendar")).andExpect(status().isOk());
        mvc.perform(get("/admin/rooms")).andExpect(status().isOk());
        mvc.perform(get("/admin/room-types")).andExpect(status().isOk());
        mvc.perform(get("/admin/checkin-requests")).andExpect(status().isOk());
        mvc.perform(get("/admin/staff-requests")).andExpect(status().isOk());
    }
}
