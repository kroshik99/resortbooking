package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.Quote;
import com.resortapi.resortbooking.entity.RoomType;
import com.resortapi.resortbooking.entity.SeasonalRate;
import com.resortapi.resortbooking.repository.SeasonalRateRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PricingService {

    private final SeasonalRateRepository seasonalRates;

    public PricingService(SeasonalRateRepository seasonalRates) {
        this.seasonalRates = seasonalRates;
    }

    /**
     * BR-05: each night is priced by the seasonal rate covering it, else the base
     * price. The total is the sum, so a stay can span a season boundary.
     */
    @Transactional(readOnly = true)
    public Quote quote(RoomType roomType, LocalDate checkIn, LocalDate checkOut) {
        List<SeasonalRate> applicable = seasonalRates.findOverlapping(roomType.getId(), checkIn, checkOut);

        BigDecimal total = BigDecimal.ZERO;
        long nights = 0;
        for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
            total = total.add(priceFor(night, roomType, applicable));
            nights++;
        }
        return new Quote(nights, total);
    }

    private BigDecimal priceFor(LocalDate night, RoomType roomType, List<SeasonalRate> applicable) {
        return applicable.stream()
                .filter(rate -> rate.covers(night))
                .findFirst()
                .map(SeasonalRate::getPrice)
                .orElseGet(roomType::getBasePrice);
    }
}
