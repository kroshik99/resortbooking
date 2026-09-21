package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.Caller;
import com.resortapi.resortbooking.dto.StaffRequestDto;
import com.resortapi.resortbooking.entity.AppUser;
import com.resortapi.resortbooking.entity.StaffRequest;
import com.resortapi.resortbooking.entity.StaffRequestStatus;
import com.resortapi.resortbooking.exception.DuplicateResourceException;
import com.resortapi.resortbooking.exception.ResourceNotFoundException;
import com.resortapi.resortbooking.repository.AppUserRepository;
import com.resortapi.resortbooking.repository.StaffRequestRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Self-service escalation from GUEST to FRONT_DESK. A registered guest can ask;
 * only an admin can grant it. Escalation to ADMIN stays a direct database action,
 * never something reachable through this flow.
 */
@Service
public class StaffRequestService {

    private final StaffRequestRepository requests;
    private final AppUserRepository users;

    public StaffRequestService(StaffRequestRepository requests, AppUserRepository users) {
        this.requests = requests;
        this.users = users;
    }

    @Transactional
    public StaffRequestDto request(Caller caller) {
        if (caller.staff()) {
            throw new DuplicateResourceException("You already have staff access.");
        }

        AppUser user = users.findByEmailIgnoreCase(caller.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", caller.email()));

        if (requests.findByUserIdAndStatus(user.getId(), StaffRequestStatus.PENDING).isPresent()) {
            throw new DuplicateResourceException("You already have a request waiting on admin approval.");
        }

        try {
            return StaffRequestDto.from(requests.saveAndFlush(new StaffRequest(user)));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a duplicate submit; there is still exactly one pending
            // request now, which is the outcome we want.
            return requests.findByUserIdAndStatus(user.getId(), StaffRequestStatus.PENDING)
                    .map(StaffRequestDto::from)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional
    public StaffRequestDto approve(Long requestId, Caller admin) {
        StaffRequest request = requests.findWithDetailsById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff request", requestId));
        AppUser reviewer = users.findByEmailIgnoreCase(admin.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", admin.email()));

        request.approve(reviewer);
        return StaffRequestDto.from(request);
    }

    @Transactional
    public StaffRequestDto reject(Long requestId, Caller admin) {
        StaffRequest request = requests.findWithDetailsById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff request", requestId));
        AppUser reviewer = users.findByEmailIgnoreCase(admin.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", admin.email()));

        request.reject(reviewer);
        return StaffRequestDto.from(request);
    }

    @Transactional(readOnly = true)
    public List<StaffRequestDto> pending() {
        return requests.findAllByStatusOrderByRequestedAtAsc(StaffRequestStatus.PENDING).stream()
                .map(StaffRequestDto::from)
                .toList();
    }

    /** For the guest-facing status page: their own most recent request, if any. */
    @Transactional(readOnly = true)
    public Optional<StaffRequestDto> latestFor(String email) {
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return requests.findFirstByUserIdOrderByRequestedAtDesc(user.getId()).map(StaffRequestDto::from);
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return requests.countByStatus(StaffRequestStatus.PENDING);
    }
}
