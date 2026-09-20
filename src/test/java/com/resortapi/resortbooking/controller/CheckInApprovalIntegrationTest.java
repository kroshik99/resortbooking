package com.resortapi.resortbooking.controller;

import com.jayway.jsonpath.JsonPath;

import com.resortapi.resortbooking.entity.AppUser;
import com.resortapi.resortbooking.entity.Role;
import com.resortapi.resortbooking.repository.AppUserRepository;
import com.resortapi.resortbooking.repository.BookingRepository;
import com.resortapi.resortbooking.repository.CheckInRequestRepository;
import com.resortapi.resortbooking.repository.GuestRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately NOT @Transactional, unlike the other *IntegrationTest classes. Each
 * MockMvc call must run its own real, independently-committing transaction - the
 * same way the live app does per HTTP request. Wrapping this test in one shared
 * transaction would hide exactly the bug it exists to catch: BookingService.checkIn()
 * and CheckInApprovalService.fileOrReportPending() both need noRollbackFor on
 * CheckInApprovalRequiredException, or the request row saved just before the
 * exception is thrown gets silently discarded by Spring's default rollback-on-any-
 * RuntimeException behaviour. A shared test transaction never actually commits or
 * rolls back mid-test, so a flushed-but-doomed row would still read back fine and
 * the regression would pass unnoticed.
 *
 * Because nothing here rolls back automatically, every row this test creates is
 * removed in @AfterEach.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckInApprovalIntegrationTest {

    private static final String FRONT_DESK_EMAIL = "checkin-test-frontdesk@example.com";
    private static final String ADMIN_EMAIL = "checkin-test-admin@example.com";
    private static final String PASSWORD = "testpass123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private GuestRepository guests;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private CheckInRequestRepository checkInRequests;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String frontDeskToken;
    private String adminToken;

    /**
     * Exactly what this test created, tracked by reference rather than discovered by
     * scanning the tables - scoped cleanup, and no need to touch a lazy association
     * (which would need an open session) to decide what belongs to this test.
     */
    private final List<String> createdBookingReferences = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        users.save(new AppUser(FRONT_DESK_EMAIL, passwordEncoder.encode(PASSWORD), Role.FRONT_DESK));
        users.save(new AppUser(ADMIN_EMAIL, passwordEncoder.encode(PASSWORD), Role.ADMIN));

        frontDeskToken = login(FRONT_DESK_EMAIL);
        adminToken = login(ADMIN_EMAIL);
    }

    /**
     * Deliberately NOT plain @Transactional. Spring's test support wraps a
     * @Transactional test/lifecycle method in a transaction that defaults to
     * ROLLBACK for isolation - exactly the opposite of what cleanup needs, and it
     * silently undid every delete here when tried. TransactionTemplate opens a real,
     * independently-committing transaction that bypasses that test machinery entirely.
     */
    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String reference : createdBookingReferences) {
                bookings.findByReference(reference).ifPresent(booking -> {
                    checkInRequests.deleteByBookingId(booking.getId());
                    bookings.delete(booking);
                });
            }
            guests.findByEmailIgnoreCase("checkin-test-guest@example.com").ifPresent(guests::delete);
            users.findByEmailIgnoreCase(FRONT_DESK_EMAIL).ifPresent(users::delete);
            users.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(users::delete);
        });
    }

    @Test
    @DisplayName("front desk checking in early files a request that survives as its own committed row, and admin approval actually checks the booking in")
    void earlyCheckInRequestSurvivesAndApprovalChecksIn() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(30);
        LocalDate checkOut = LocalDate.now().plusDays(32);

        String bookingBody = mvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(frontDeskToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomTypeId":4,"checkIn":"%s","checkOut":"%s","numGuests":2,
                                 "guest":{"fullName":"CheckIn Test Guest","email":"checkin-test-guest@example.com"}}
                                """.formatted(checkIn, checkOut)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(bookingBody, "$.reference");
        createdBookingReferences.add(reference);

        mvc.perform(post("/api/v1/bookings/{ref}/confirm", reference)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        // The regression this test exists for: this call's transaction must commit
        // the CheckInRequest row even though it also throws.
        mvc.perform(post("/api/v1/bookings/{ref}/check-in", reference)
                        .header(HttpHeaders.AUTHORIZATION, bearer(frontDeskToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_REQUIRED"));

        // A SEPARATE request, its own transaction - this is what actually proves the
        // earlier one committed rather than silently rolling back.
        String pending = mvc.perform(get("/api/v1/checkin-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(pending).contains(reference);

        // Booking must still be CONFIRMED - the blocked check-in must not have applied.
        mvc.perform(get("/api/v1/bookings/{ref}", reference)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Number requestId = JsonPath.read(pending, "$[0].id");
        mvc.perform(post("/api/v1/checkin-requests/{id}/approve", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get("/api/v1/bookings/{ref}", reference)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));
    }

    @Test
    @DisplayName("admin's own check-in bypasses the date gate with no request filed")
    void adminCheckInBypassesTheGate() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(35);
        LocalDate checkOut = LocalDate.now().plusDays(37);

        String bookingBody = mvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomTypeId":3,"checkIn":"%s","checkOut":"%s","numGuests":2,
                                 "guest":{"fullName":"CheckIn Test Guest","email":"checkin-test-guest@example.com"}}
                                """.formatted(checkIn, checkOut)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(bookingBody, "$.reference");
        createdBookingReferences.add(reference);

        mvc.perform(post("/api/v1/bookings/{ref}/confirm", reference)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/bookings/{ref}/check-in", reference)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
