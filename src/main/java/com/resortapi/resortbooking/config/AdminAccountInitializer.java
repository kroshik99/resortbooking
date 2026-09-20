package com.resortapi.resortbooking.config;

import com.resortapi.resortbooking.entity.AppUser;
import com.resortapi.resortbooking.entity.Role;
import com.resortapi.resortbooking.repository.AppUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN from environment variables. Deliberately not a migration:
 * a password or hash must never be committed.
 */
@Component
public class AdminAccountInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminAccountInitializer(AppUserRepository users,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.admin.email:}") String email,
                                   @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (email.isBlank() || password.isBlank()) {
            log.info("No ADMIN_EMAIL/ADMIN_PASSWORD set; skipping admin bootstrap.");
            return;
        }
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        users.save(new AppUser(email, passwordEncoder.encode(password), Role.ADMIN));
        log.info("Created initial ADMIN account for {}", email);
    }
}
