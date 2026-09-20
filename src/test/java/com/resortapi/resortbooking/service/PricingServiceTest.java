package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.Quote;
import com.resortapi.resortbooking.entity.RoomType;
import com.resortapi.resortbooking.entity.SeasonalRate;
import com.resortapi.resortbooking.repository.SeasonalRateRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** BR-05 and BR-06, with the repository mocked so no database is involved. */
@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);
    private static final LocalDate OCT_13 = LocalDate.of(2026, 10, 13);
    private static final LocalDate OCT_14 = LocalDate.of(2026, 10, 14);

    @Mock
    private SeasonalRateRepository seasonalRates;

    @InjectMocks
    private PricingService pricingService;

    private final RoomType deluxe = new RoomType("Deluxe", 3, new BigDecimal("3500.00"), null);

    @Test
    @DisplayName("with no seasonal rate, every night costs the base price")
    void usesBasePriceWhenNoSeasonalRate() {
        when(seasonalRates.findOverlapping(any(), any(), any())).thenReturn(List.of());

        Quote quote = pricingService.quote(deluxe, OCT_12, OCT_14);

        assertThat(quote.nights()).isEqualTo(2);
        assertThat(quote.total()).isEqualByComparingTo("7000.00");
    }

    @Test
    @DisplayName("TC-05: a rate covering only Oct 13 gives base + seasonal, not two seasonal nights")
    void mixesBaseAndSeasonalAcrossTheStay() {
        when(seasonalRates.findOverlapping(any(), any(), any()))
                .thenReturn(List.of(new SeasonalRate(deluxe, OCT_13, OCT_14, new BigDecimal("5000.00"))));

        Quote quote = pricingService.quote(deluxe, OCT_12, OCT_14);

        // Oct 12 at 3500 (base) + Oct 13 at 5000 (seasonal). Oct 14 is checkout, not a night.
        assertThat(quote.total()).isEqualByComparingTo("8500.00");
        assertThat(quote.nights()).isEqualTo(2);
    }

    @Test
    @DisplayName("a rate spanning the whole stay prices every night at the seasonal rate")
    void appliesSeasonalRateToEveryNight() {
        when(seasonalRates.findOverlapping(any(), any(), any()))
                .thenReturn(List.of(new SeasonalRate(deluxe, OCT_12, OCT_14, new BigDecimal("5000.00"))));

        assertThat(pricingService.quote(deluxe, OCT_12, OCT_14).total())
                .isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("the checkout date is never charged, even when a rate starts on it")
    void doesNotChargeTheCheckoutNight() {
        when(seasonalRates.findOverlapping(any(), any(), any()))
                .thenReturn(List.of(new SeasonalRate(deluxe, OCT_14, OCT_14.plusDays(5), new BigDecimal("9999.00"))));

        assertThat(pricingService.quote(deluxe, OCT_12, OCT_14).total())
                .isEqualByComparingTo("7000.00");
    }

    @Test
    @DisplayName("BR-07: the total stays exact decimal, never a float")
    void keepsMoneyExact() {
        RoomType odd = new RoomType("Odd", 2, new BigDecimal("0.10"), null);
        when(seasonalRates.findOverlapping(any(), any(), any())).thenReturn(List.of());

        // 0.10 x 3 is 0.30 exactly; in double arithmetic it would be 0.30000000000000004.
        assertThat(pricingService.quote(odd, OCT_12, OCT_12.plusDays(3)).total())
                .isEqualByComparingTo("0.30");
    }

    @Test
    @DisplayName("a one night stay is charged once")
    void chargesSingleNightOnce() {
        when(seasonalRates.findOverlapping(any(), any(), any())).thenReturn(List.of());

        Quote quote = pricingService.quote(deluxe, OCT_12, OCT_13);

        assertThat(quote.nights()).isEqualTo(1);
        assertThat(quote.total()).isEqualByComparingTo("3500.00");
    }
}
