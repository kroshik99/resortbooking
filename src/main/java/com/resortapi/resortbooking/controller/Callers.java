package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.dto.Caller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Turns Spring Security's Authentication into the plain Caller the services take. */
final class Callers {

    private Callers() {
    }

    static Caller of(Authentication authentication) {
        boolean admin = hasRole(authentication, "ROLE_ADMIN");
        boolean staff = admin || hasRole(authentication, "ROLE_FRONT_DESK");
        return new Caller(authentication.getName(), staff, admin);
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
