package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.Caller;
import com.resortapi.resortbooking.dto.CheckInRequestDto;
import com.resortapi.resortbooking.entity.AppUser;
import com.resortapi.resortbooking.entity.Booking;
import com.resortapi.resortbooking.entity.CheckInRequest;
import com.resortapi.resortbooking.entity.CheckInRequestStatus;
import com.resortapi.resortbooking.exception.CheckInApprovalRequiredException;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.repository.AppUserRepository;
import com.resortapi.resortbooking.repository.CheckInRequestRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Front desk can only check a guest in on the booking's actual check-in date;
 * outside that window the attempt becomes a request an admin must review. Admin's
 * own check-in bypasses this entirely - their role is the approval.
 */
@Service
public class CheckInApprovalService {

    private final CheckInRequestRepository requests;
    private final AppUserRepository users;

    public CheckInApprovalService(CheckInRequestRepository requests, AppUserRepository users) {
        this.requests = requests;
        this.users = users;
    }

    /**
     * Files a request for this booking, or reports the one already pending. Always
     * ends by throwing - the caller (front desk) never gets an immediate check-in.
     *
     * noRollbackFor matters here: by default Spring rolls back the whole transaction
     * on any unchecked exception, which would silently discard the very save() this
     * method exists to make. The same exemption must also be on BookingService.checkIn,
     * since that outer @Transactional boundary would otherwise re-apply the default
     * rollback rule as the exception passes through it.
     */
    @Transactional(noRollbackFor = CheckInApprovalRequiredException.class)
    public void fileOrReportPending(Booking booking, Caller caller) {
        var existing = requests.findByBookingIdAndStatus(booking.getId(), CheckInRequestStatus.PENDING);
        if (existing.isPresent()) {
            throw new CheckInApprovalRequiredException(
                    "A check-in request for " + booking.getReference()
                            + " is already waiting on admin approval.");
        }

        AppUser requestedBy = users.findByEmailIgnoreCase(caller.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", caller.email()));

        try {
            requests.saveAndFlush(new CheckInRequest(booking, requestedBy));
        } catch (DataIntegrityViolationException e) {
            // Someone else's concurrent request won the same race; there is still
            // exactly one pending request now, which is the outcome we want.
        }

        throw new CheckInApprovalRequiredException(
                booking.getReference() + " is not due for check-in until "
                        + booking.getCheckIn() + ". A request has been sent to admin for approval.");
    }

    @Transactional
    public CheckInRequestDto approve(Long requestId, Caller admin) {
        CheckInRequest request = requests.findWithDetailsById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Check-in request", requestId));
        AppUser reviewer = users.findByEmailIgnoreCase(admin.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", admin.email()));

        request.approve(reviewer);
        return CheckInRequestDto.from(request);
    }

    @Transactional
    public CheckInRequestDto reject(Long requestId, Caller admin) {
        CheckInRequest request = requests.findWithDetailsById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Check-in request", requestId));
        AppUser reviewer = users.findByEmailIgnoreCase(admin.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", admin.email()));

        request.reject(reviewer);
        return CheckInRequestDto.from(request);
    }

    @Transactional(readOnly = true)
    public List<CheckInRequestDto> pending() {
        return requests.findAllByStatusOrderByRequestedAtAsc(CheckInRequestStatus.PENDING).stream()
                .map(CheckInRequestDto::from)
                .toList();
    }

    /** For the booking detail page: is there already a pending request for this booking? */
    @Transactional(readOnly = true)
    public Optional<CheckInRequestDto> pendingFor(String bookingReference) {
        return requests.findByBookingReferenceAndStatus(bookingReference, CheckInRequestStatus.PENDING)
                .map(CheckInRequestDto::from);
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return requests.countByStatus(CheckInRequestStatus.PENDING);
    }
}
