package com.resortapi.resortbooking.entity;

import com.resortapi.resortbooking.exception.InvalidStatusTransitionException;
import com.resortapi.resortbooking.service.BookingDateRules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id")
    private Guest guest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "num_guests", nullable = false)
    private int numGuests;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    private int version;

    protected Booking() {
    }

    public Booking(String reference, Guest guest, Room room,
                   LocalDate checkIn, LocalDate checkOut,
                   int numGuests, BigDecimal totalPrice) {
        this.reference = reference;
        this.guest = guest;
        this.room = room;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.numGuests = numGuests;
        this.totalPrice = totalPrice;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now(BookingDateRules.RESORT_ZONE);
    }

    public void confirm() {
        transitionTo(BookingStatus.CONFIRMED);
    }

    public void checkIn() {
        transitionTo(BookingStatus.CHECKED_IN);
    }

    public void checkOut() {
        transitionTo(BookingStatus.CHECKED_OUT);
    }

    public void cancel() {
        transitionTo(BookingStatus.CANCELLED);
    }

    /**
     * Moves the stay itself - dates and party size - while the booking hasn't
     * started yet. The same window BR-09 allows a cancellation in; changing the
     * room is a different booking, not an edit, so it stays out of this method.
     */
    public void reschedule(LocalDate checkIn, LocalDate checkOut, int numGuests, BigDecimal totalPrice) {
        if (status != BookingStatus.PENDING && status != BookingStatus.CONFIRMED) {
            throw new InvalidStatusTransitionException("booking", status.name(), "EDITED");
        }
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.numGuests = numGuests;
        this.totalPrice = totalPrice;
    }

    private void transitionTo(BookingStatus next) {
        if (!status.canMoveTo(next)) {
            throw new InvalidStatusTransitionException(status, next);
        }
        status = next;
    }

    /** BR-02: check-out is exclusive, so Oct 12 to Oct 14 is 2 nights. */
    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Guest getGuest() {
        return guest;
    }

    public Room getRoom() {
        return room;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public int getNumGuests() {
        return numGuests;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public int getVersion() {
        return version;
    }
}
