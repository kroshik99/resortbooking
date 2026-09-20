package com.resortapi.resortbooking.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** BR-04: guest count cannot exceed the room type's capacity. */
class RoomTypeTest {

    private final RoomType standard = new RoomType("Standard", 2, new BigDecimal("2500.00"), null);

    @Test
    @DisplayName("fewer guests than capacity fits")
    void acceptsFewerGuests() {
        assertThat(standard.accommodates(1)).isTrue();
    }

    @Test
    @DisplayName("exactly the capacity fits")
    void acceptsExactCapacity() {
        assertThat(standard.accommodates(2)).isTrue();
    }

    @Test
    @DisplayName("TC-04: one guest over capacity does not fit")
    void rejectsOverCapacity() {
        assertThat(standard.accommodates(3)).isFalse();
    }
}
