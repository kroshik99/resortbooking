package com.resortapi.resortbooking.controller;

import com.jayway.jsonpath.JsonPath;

import com.resortapi.resortbooking.entity.AppUser;
import com.resortapi.resortbooking.entity.Role;
import com.resortapi.resortbooking.repository.AppUserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fills a gap noted in code_review.md (H-1): this feature had only entity-level unit
 * tests (StaffRequestTest) before this - nothing exercised role gating or the actual
 * HTTP behaviour through the real security chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StaffRequestApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("a guest can request staff access, and it shows up in the admin queue")
    void guestCanRequestAndAdminSeesIt() throws Exception {
        String guestToken = registerAndLogin("staffreq-guest@example.com", "Staff Req Guest");
        String adminToken = adminToken("staffreq-admin@example.com");

        mvc.perform(post("/api/v1/staff-requests").header(HttpHeaders.AUTHORIZATION, bearer(guestToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.userEmail").value("staffreq-guest@example.com"));

        mvc.perform(get("/api/v1/staff-requests").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userEmail=='staffreq-guest@example.com')]").exists());
    }

    @Test
    @DisplayName("a second request while one is pending is rejected, not duplicated")
    void duplicateRequestIsRejected() throws Exception {
        String guestToken = registerAndLogin("staffreq-dup@example.com", "Dup Guest");

        mvc.perform(post("/api/v1/staff-requests").header(HttpHeaders.AUTHORIZATION, bearer(guestToken)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/staff-requests").header(HttpHeaders.AUTHORIZATION, bearer(guestToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("only an admin may list, approve or reject requests")
    void listApproveRejectAreAdminOnly() throws Exception {
        String guestToken = registerAndLogin("staffreq-nonadmin@example.com", "Non Admin");

        mvc.perform(get("/api/v1/staff-requests").header(HttpHeaders.AUTHORIZATION, bearer(guestToken)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/staff-requests/1/approve").header(HttpHeaders.AUTHORIZATION, bearer(guestToken)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/staff-requests/1/reject").header(HttpHeaders.AUTHORIZATION, bearer(guestToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("approving a request actually promotes the account to FRONT_DESK")
    void approvalPromotesTheAccount() throws Exception {
        String guestToken = registerAndLogin("staffreq-promoted@example.com", "Promoted Guest");
        String adminToken = adminToken("staffreq-admin2@example.com");

        String body = mvc.perform(post("/api/v1/staff-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number requestId = JsonPath.read(body, "$.id");

        mvc.perform(post("/api/v1/staff-requests/{id}/approve", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Role changes take effect on the very next request - no need to re-login,
        // since the JWT filter re-derives the role from the database each time.
        mvc.perform(get("/api/v1/rooms").header(HttpHeaders.AUTHORIZATION, bearer(guestToken)))
                .andExpect(status().isOk());
    }

    private String adminToken(String email) throws Exception {
        users.save(new AppUser(email, passwordEncoder.encode("testpass123"), Role.ADMIN));
        return login(email, "testpass123");
    }

    private String registerAndLogin(String email, String fullName) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","password":"testpass123"}
                                """.formatted(fullName, email)))
                .andExpect(status().isCreated());
        return login(email, "testpass123");
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
