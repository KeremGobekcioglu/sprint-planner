package com.sprintplanner.web.auth;

import com.sprintplanner.domain.model.AppUser;
import com.sprintplanner.infrastructure.auth.JwtService;
import com.sprintplanner.infrastructure.persistence.JpaUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;

/**
 * Authentication endpoints for login and registration
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JpaUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(JpaUserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody AuthRequest request) {
        // Validate input
        String email = request.email() == null ? null : request.email().trim();
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (request.password() == null || request.password().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        }

        // Check email not already taken
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        String name = (request.name() == null || request.name().isBlank())
            ? deriveDisplayName(email)
            : request.name().trim();
        String role = userRepository.count() == 0 ? "ADMIN" : "USER";

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
        // Find user by email
        AppUser user = userRepository.findByEmail(request.email())
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

