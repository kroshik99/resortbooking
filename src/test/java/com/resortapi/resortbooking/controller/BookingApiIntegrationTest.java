package com.resortapi.resortbooking.controller;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full request to database, through the real security chain. Each test rolls back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingApiIntegrationTest {

    private static final String CHECK_IN = LocalDate.now().plusDays(40).toString();
    private static final String CHECK_OUT = LocalDate.now().plusDays(42).toString();

    @Autowired
    private MockMvc mvc;

    private String anaToken;
    private String benToken;

    @BeforeEach
    void registerTwoGuests() throws Exception {
        anaToken = registerAndLogin("ana.test@example.com", "Ana Reyes");
        benToken = registerAndLogin("ben.test@example.com", "Ben Cruz");
    }

    @Test
    @DisplayName("availability is public but the room list is not")
    void publicAndProtectedEndpoints() throws Exception {
        mvc.perform(get("/api/v1/availability")
                        .param("checkIn", CHECK_IN).param("checkOut", CHECK_OUT).param("guests", "2"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/rooms")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/rooms").header(HttpHeaders.AUTHORIZATION, bearer(anaToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a guest booking is recorded against the caller, not the email in the body")
    void bookingIgnoresGuestDetailsFromTheBody() throws Exception {
        String body = mvc.perform(bookingRequest(anaToken)
                        .content(bookingJson(1, CHECK_IN, CHECK_OUT, 2, "attacker@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guestName").value("Ana Reyes"))
                .andExpect(jsonPath("$.currency").value("PHP"))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(body, "$.reference")).startsWith("RB-");
    }

    @Test
    @DisplayName("TC-06: another guest's booking reads as 404, never 403")
    void otherGuestsBookingIsNotFound() throws Exception {
        String reference = createBooking(anaToken);

        mvc.perform(get("/api/v1/bookings/{ref}", reference)
                        .header(HttpHeaders.AUTHORIZATION, bearer(anaToken)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/bookings/{ref}", reference)
                        .header(HttpHeaders.AUTHORIZATION, bearer(benToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-04: too many guests for the room type is 422")
    void capacityExceeded() throws Exception {
        mvc.perform(bookingRequest(anaToken)
                        .content(bookingJson(1, CHECK_IN, CHECK_OUT, 3, "ana.test@example.com")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"));
    }

    @Test
    @DisplayName("BR-03: a check-in in the past is 400")
    void pastCheckInRejected() throws Exception {
        mvc.perform(bookingRequest(anaToken)
                        .content(bookingJson(1, "2020-01-01", "2020-01-03", 2, "ana.test@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATES"));
    }

    @Test
    @DisplayName("bean validation reports every bad field at once")
    void validationListsAllFields() throws Exception {
        mvc.perform(bookingRequest(anaToken)
                        .content("{\"roomTypeId\":null,\"checkIn\":null,\"checkOut\":null,\"numGuests\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.roomTypeId").exists())
                .andExpect(jsonPath("$.errors.numGuests").exists());
    }

    @Test
    @DisplayName("check-in is staff only")
    void checkInIsStaffOnly() throws Exception {
        String reference = createBooking(anaToken);

        mvc.perform(post("/api/v1/bookings/{ref}/check-in", reference)
                        .header(HttpHeaders.AUTHORIZATION, bearer(anaToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a malformed token is rejected, not treated as anonymous-but-allowed")
    void malformedTokenRejected() throws Exception {
        mvc.perform(get("/api/v1/rooms").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.real.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the wrong password returns 401, and says nothing about which part was wrong")
    void wrongPasswordRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana.test@example.com\",\"password\":\"definitely-wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong email or password"));
    }

    private String createBooking(String token) throws Exception {
        String body = mvc.perform(bookingRequest(token)
                        .content(bookingJson(1, CHECK_IN, CHECK_OUT, 2, "ana.test@example.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.reference");
    }

    private MockHttpServletRequestBuilder bookingRequest(String token) {
        return post("/api/v1/bookings")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static String bookingJson(long roomTypeId, String checkIn, String checkOut, int guests, String email) {
        return """
                {"roomTypeId":%d,"checkIn":"%s","checkOut":"%s","numGuests":%d,
                 "guest":{"fullName":"Body Name","email":"%s"}}
                """.formatted(roomTypeId, checkIn, checkOut, guests, email);
    }

    private String registerAndLogin(String email, String fullName) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","password":"testpass123"}
                                """.formatted(fullName, email)))
                .andExpect(status().isCreated());

        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"testpass123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
