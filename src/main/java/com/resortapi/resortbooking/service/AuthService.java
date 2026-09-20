package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.dto.LoginRequest;
import com.resortapi.resortbooking.dto.LoginResponse;
import com.resortapi.resortbooking.dto.RegisterRequest;
import com.resortapi.resortbooking.entity.AppUser;
import com.resortapi.resortbooking.entity.Guest;
import com.resortapi.resortbooking.entity.Role;
import com.resortapi.resortbooking.exception.DuplicateResourceException;
import com.resortapi.resortbooking.repository.AppUserRepository;
import com.resortapi.resortbooking.repository.GuestRepository;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final GuestRepository guests;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AppUserRepository users, GuestRepository guests, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.guests = guests;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** FR-05. A new account always gets the GUEST role; staff are created by an admin. */
    @Transactional
    public void register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("An account already exists for " + request.email());
        }

        AppUser user = users.save(new AppUser(
                request.email(), passwordEncoder.encode(request.password()), Role.GUEST));

        // A walk-in may already exist with this email; link it rather than duplicating.
        guests.findByEmailIgnoreCase(request.email())
                .ifPresentOrElse(
                        existing -> existing.linkTo(user),
                        () -> guests.save(new Guest(request.fullName(), request.email(), request.phone(), user)));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException e) {
            // Same message whether the email is unknown or the password is wrong.
            throw new BadCredentialsException("Wrong email or password");
        }

        AppUser user = users.findByEmailIgnoreCase(request.email()).orElseThrow();
        return new LoginResponse(
                jwtService.issue(user), "Bearer", jwtService.getExpirySeconds(), user.getRole().name());
    }
}
