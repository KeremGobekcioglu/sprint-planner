package com.sprintplanner.web.auth;

/**
 * Auth request DTO for login and registration
 */
public record AuthRequest(
    String email,
    String password
) {}

