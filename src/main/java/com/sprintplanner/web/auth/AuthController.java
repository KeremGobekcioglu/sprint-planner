package com.sprintplanner.web.auth;

import com.sprintplanner.domain.model.AppUser;
import com.sprintplanner.infrastructure.auth.JwtService;
import com.sprintplanner.infrastructure.persistence.JpaUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Authentication endpoints for login and registration
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JpaUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final boolean registrationEnabled;
    private final boolean firstUserIsAdmin;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public AuthController(
        JpaUserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        @Value("${app.registration-enabled:true}") boolean registrationEnabled,
        @Value("${app.first-user-is-admin:true}") boolean firstUserIsAdmin
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.registrationEnabled = registrationEnabled;
        this.firstUserIsAdmin = firstUserIsAdmin;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody AuthRequest request) {
        if (!registrationEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration is currently closed");
        }

        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        if (email.isBlank() || email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required");
        }
        // bcrypt only uses the first 72 bytes, so cap the length to avoid silent truncation
        if (request.password() == null || request.password().length() < 8 || request.password().length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be between 8 and 72 characters");
        }
        if (request.name() != null && request.name().trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is too long");
        }

        // Check email not already taken
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        String name = (request.name() == null || request.name().isBlank())
            ? deriveDisplayName(email)
            : request.name().trim();
        // In production the admin is created from ADMIN_EMAIL/ADMIN_PASSWORD (AdminBootstrap),
        // so the first public signup must never become admin.
        String role = firstUserIsAdmin && userRepository.count() == 0 ? "ADMIN" : "USER";

        // Hash password and save user
        UUID userId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(request.password());
        AppUser user = new AppUser(userId, email, name, role, passwordHash, Instant.now());
        userRepository.save(user);

        // Generate token
        String token = jwtService.generateToken(userId, role);
        return ResponseEntity.ok(new AuthResponse(token, userId.toString(), user.getEmail(), user.getName(), user.getRole()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        if (request.email() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        AppUser user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Generate token
        String token = jwtService.generateToken(user.getId(), user.getRole());
        return ResponseEntity.ok(new AuthResponse(token, user.getId().toString(), user.getEmail(), user.getName(), user.getRole()));
    }

    private String deriveDisplayName(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }
}

