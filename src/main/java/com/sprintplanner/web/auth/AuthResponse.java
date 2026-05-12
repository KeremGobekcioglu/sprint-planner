package com.sprintplanner.web.auth;

/**
 * Auth response DTO - returned on successful login/registration
 */
public record AuthResponse(
    String token,
    String userId,
    String email,
    String name,
    String role
) {}

