package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.BookingDto;
import com.resortapi.resortbooking.dto.Caller;
import com.resortapi.resortbooking.dto.CreateBookingRequest;
import com.resortapi.resortbooking.dto.GuestDetails;
import com.resortapi.resortbooking.dto.Quote;
import com.resortapi.resortbooking.entity.Booking;
import com.resortapi.resortbooking.entity.Guest;
import com.resortapi.resortbooking.entity.Room;
import com.resortapi.resortbooking.entity.RoomType;
import com.resortapi.resortbooking.exception.CapacityExceededException;
import com.resortapi.resortbooking.exception.CheckInApprovalRequiredException;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.exception.RoomNotAvailableException;
import com.resortapi.resortbooking.repository.BookingRepository;
import com.resortapi.resortbooking.repository.GuestRepository;
import com.resortapi.resortbooking.repository.RoomRepository;
import com.resortapi.resortbooking.repository.RoomTypeRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final RoomRepository rooms;
    private final RoomTypeRepository roomTypes;
    private final GuestRepository guests;
    private final PricingService pricingService;
    private final CheckInApprovalService checkInApprovalService;

    public BookingService(BookingRepository bookings, RoomRepository rooms, RoomTypeRepository roomTypes,
                          GuestRepository guests, PricingService pricingService,
                          CheckInApprovalService checkInApprovalService) {
        this.bookings = bookings;
        this.rooms = rooms;
        this.roomTypes = roomTypes;
        this.guests = guests;
        this.pricingService = pricingService;
        this.checkInApprovalService = checkInApprovalService;
    }

    /**
     * FR-03. One transaction: lock the candidate rooms, re-check the overlap, then
     * insert. The exclusion constraint in the schema is the backstop if anything
     * still slips through (NFR-02).
     */
    @Transactional
    public BookingDto create(CreateBookingRequest request, Caller caller) {
        BookingDateRules.check(request.checkIn(), request.checkOut());

        RoomType roomType = roomTypes.findById(request.roomTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Room type", request.roomTypeId()));

        // BR-04
        if (!roomType.accommodates(request.numGuests())) {
            throw new CapacityExceededException(roomType.getName(), roomType.getCapacity(), request.numGuests());
        }

        // BR-01 + BR-10, with SELECT ... FOR UPDATE
        List<Room> free = rooms.findFreeRoomsForUpdate(roomType.getId(), request.checkIn(), request.checkOut());
        if (free.isEmpty()) {
            throw new RoomNotAvailableException(roomType.getName(), request.checkIn(), request.checkOut());
        }

        Guest guest = resolveGuest(request, caller);

        // BR-06: the agreed total is stored, so later rate changes never alter it.
        Quote quote = pricingService.quote(roomType, request.checkIn(), request.checkOut());

        Booking booking = new Booking(nextReference(), guest, free.get(0),
                request.checkIn(), request.checkOut(), request.numGuests(), quote.total());

        try {
            // Flush here so the exclusion constraint reports now, not at commit.
            return BookingDto.from(bookings.saveAndFlush(booking));
        } catch (DataIntegrityViolationException e) {
            // NFR-02: the row lock cannot reserve a row that does not exist yet, so
            // two requests can both see the room as free. The database settles it.
            throw new RoomNotAvailableException(roomType.getName(), request.checkIn(), request.checkOut());
        }
    }

    /** FR-04: the guest's own bookings, newest stay first. */
    @Transactional(readOnly = true)
    public List<BookingDto> findForGuest(String email) {
        return bookings.findByGuestEmailIgnoreCaseOrderByCheckInDesc(email).stream()
                .map(BookingDto::from)
                .toList();
    }

    /** FR-09: staff search by reference or guest name, capped at 50 results. */
    @Transactional(readOnly = true)
    public List<BookingDto> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return bookings.findTop50ByReferenceContainingIgnoreCaseOrGuestFullNameContainingIgnoreCaseOrderByCheckInDesc(q, q)
                .stream()
                .map(BookingDto::from)
                .toList();
    }

    /** Prefills the details form for a signed-in guest. */
    @Transactional(readOnly = true)
    public GuestDetails profileFor(String email) {
        return guests.findByEmailIgnoreCase(email)
                .map(guest -> new GuestDetails(guest.getFullName(), guest.getEmail(), guest.getPhone()))
                .orElseGet(() -> new GuestDetails("", email, ""));
    }

    @Transactional(readOnly = true)
    public BookingDto findByReference(String reference, Caller caller) {
        Booking booking = require(reference);
        requireVisibleTo(booking, caller);
        return BookingDto.from(booking);
    }

    @Transactional
    public BookingDto confirm(String reference) {
        Booking booking = require(reference);
        booking.confirm();
        return BookingDto.from(booking);
    }

    /** BR-09: a guest may cancel only their own booking, and only before check-in. */
    @Transactional
    public BookingDto cancel(String reference, Caller caller) {
        Booking booking = require(reference);
        requireVisibleTo(booking, caller);
        booking.cancel();
        return BookingDto.from(booking);
    }

    /**
     * Front desk may only check a guest in on the booking's actual check-in date;
     * outside that window the attempt files an admin approval request instead.
     * Admin's own check-in is never gated - their role is the override.
     *
     * noRollbackFor: CheckInApprovalRequiredException must still let the request
     * saved in fileOrReportPending() commit, even though it propagates through this
     * transaction too. Without it here as well, this method's default rollback rule
     * re-marks the shared transaction rollback-only and silently discards that save.
     */
    @Transactional(noRollbackFor = CheckInApprovalRequiredException.class)
    public BookingDto checkIn(String reference, Caller caller) {
        Booking booking = require(reference);

        if (!caller.admin() && !booking.getCheckIn().equals(BookingDateRules.today())) {
            checkInApprovalService.fileOrReportPending(booking, caller);
        }

        booking.checkIn();
        return BookingDto.from(booking);
    }

    @Transactional
    public BookingDto checkOut(String reference) {
        Booking booking = require(reference);
        booking.checkOut();
        return BookingDto.from(booking);
    }

    /**
     * The owning guest or staff may move a booking's dates/party size while it is
     * still PENDING or CONFIRMED. Re-validates everything create() does, against the
     * booking's own current room - changing the room itself is cancel-and-rebook,
     * not an edit.
     */
    @Transactional
    public BookingDto reschedule(String reference, LocalDate checkIn, LocalDate checkOut, int numGuests,
                                 Caller caller) {
        Booking booking = require(reference);
        requireVisibleTo(booking, caller);

        BookingDateRules.check(checkIn, checkOut);

        RoomType roomType = booking.getRoom().getRoomType();
        if (!roomType.accommodates(numGuests)) {
            throw new CapacityExceededException(roomType.getName(), roomType.getCapacity(), numGuests);
        }

        if (bookings.existsOverlapping(booking.getRoom().getId(), checkIn, checkOut, booking.getId())) {
            throw new RoomNotAvailableException(roomType.getName(), checkIn, checkOut);
        }

        Quote quote = pricingService.quote(roomType, checkIn, checkOut);

        try {
            booking.reschedule(checkIn, checkOut, numGuests, quote.total());
            bookings.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            // Same backstop as create(): the pre-check above narrows the race but the
            // exclusion constraint is what actually closes it.
            throw new RoomNotAvailableException(roomType.getName(), checkIn, checkOut);
        }
        return BookingDto.from(booking);
    }

    private Booking require(String reference) {
        return bookings.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", reference));
    }

    /**
     * A booking someone else owns is reported as missing, not forbidden. A 403 would
     * confirm the reference exists and let an attacker enumerate them.
     */
    private void requireVisibleTo(Booking booking, Caller caller) {
        if (!caller.staff() && !booking.getGuest().getEmail().equalsIgnoreCase(caller.email())) {
            throw new ResourceNotFoundException("Booking", booking.getReference());
        }
    }

    /** Staff may book on behalf of a walk-in; a guest always books as themselves. */
    private Guest resolveGuest(CreateBookingRequest request, Caller caller) {
        if (caller.staff()) {
            return findOrCreateGuest(request.guest());
        }
        return guests.findByEmailIgnoreCase(caller.email())
                .orElseThrow(() -> new ResourceNotFoundException("Guest profile for", caller.email()));
    }

    private Guest findOrCreateGuest(GuestDetails details) {
        return guests.findByEmailIgnoreCase(details.email())
                .orElseGet(() -> guests.save(new Guest(details.fullName(), details.email(), details.phone())));
    }

    /** BR-11: RB-2026-00001, from a sequence rather than the row id. */
    private String nextReference() {
        return "RB-%d-%05d".formatted(BookingDateRules.today().getYear(), bookings.nextReferenceNumber());
    }
}
