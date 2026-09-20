package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.AvailabilityOptionDto;
import com.resortapi.resortbooking.dto.Quote;
import com.resortapi.resortbooking.dto.QuoteView;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.entity.Room;
import com.resortapi.resortbooking.entity.RoomType;
import com.resortapi.resortbooking.repository.RoomRepository;
import com.resortapi.resortbooking.repository.RoomTypeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AvailabilityService {

    private final RoomTypeRepository roomTypes;
    private final RoomRepository rooms;
    private final PricingService pricingService;

    public AvailabilityService(RoomTypeRepository roomTypes, RoomRepository rooms, PricingService pricingService) {
        this.roomTypes = roomTypes;
        this.rooms = rooms;
        this.pricingService = pricingService;
    }

    /** Price one room type for these dates, for the details and review pages. */
    @Transactional(readOnly = true)
    public QuoteView quoteFor(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        BookingDateRules.check(checkIn, checkOut);

        RoomType roomType = roomTypes.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type", roomTypeId));
        Quote quote = pricingService.quote(roomType, checkIn, checkOut);

        return new QuoteView(roomType.getId(), roomType.getName(), roomType.getCapacity(),
                quote.nights(), quote.total().toPlainString(), "PHP");
    }

    /** FR-01 + FR-02: room types with at least one free room, priced for these dates. */
    @Transactional(readOnly = true)
    public List<AvailabilityOptionDto> search(LocalDate checkIn, LocalDate checkOut, int guests, Long roomTypeId) {
        BookingDateRules.check(checkIn, checkOut);

        List<AvailabilityOptionDto> options = new ArrayList<>();
        for (RoomType roomType : roomTypes.findAllByOrderByNameAsc()) {
            if (roomTypeId != null && !roomTypeId.equals(roomType.getId())) {
                continue;
            }
            if (!roomType.accommodates(guests)) {
                continue;
            }
            List<Room> free = rooms.findFreeRooms(roomType.getId(), checkIn, checkOut);
            if (free.isEmpty()) {
                continue;
            }
            Quote quote = pricingService.quote(roomType, checkIn, checkOut);
            options.add(new AvailabilityOptionDto(
                    roomType.getId(),
                    roomType.getName(),
                    roomType.getCapacity(),
                    quote.nights(),
                    quote.total().toPlainString(),
                    "PHP",
                    free.size()));
        }
        return options;
    }
}
