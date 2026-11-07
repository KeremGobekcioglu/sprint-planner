package com.sprintplanner.infrastructure.config;

import com.sprintplanner.domain.model.AppUser;
import com.sprintplanner.infrastructure.persistence.JpaUserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Production only: creates the single ADMIN account from ADMIN_EMAIL / ADMIN_PASSWORD on startup
 * (if it does not exist yet). Public signups can never become admin in prod.
 */
@Component
@Profile("prod")
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final JpaUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
        JpaUserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${app.admin.email:}") String adminEmail,
        @Value("${app.admin.password:}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("[AdminBootstrap] ADMIN_EMAIL/ADMIN_PASSWORD not set - no admin account will exist.");
            return;
        }
        if (adminPassword.length() < 12 || adminPassword.length() > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD must be 12-72 characters.");
        }
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }
        userRepository.save(new AppUser(
            UUID.randomUUID(), adminEmail, "Admin", "ADMIN",
            passwordEncoder.encode(adminPassword), Instant.now()
        ));
        log.info("[AdminBootstrap] Created admin account {}", adminEmail);
    }
}
