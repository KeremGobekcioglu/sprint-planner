package com.sprintplanner.infrastructure.auth;

import java.util.UUID;

/**
 * Service for generating and validating JWT tokens
 */
public interface JwtService {
    /**
     * Generate a JWT token for the given user ID
     */
    String generateToken(UUID userId);

    /**
     * Generate a JWT token for the given user ID and role
     */
    String generateToken(UUID userId, String role);

    /**
     * Validate a JWT token and return true if valid
     */
    boolean validateToken(String token);

    /**
     * Extract the user ID from a valid JWT token
     */
    UUID extractUserId(String token);

    /**
     * Extract the role claim from a valid JWT token
     */
    String extractRole(String token);
}

