package com.sprintplanner.infrastructure.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT authentication filter — validates token on every request
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);
    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractTokenFromRequest(request);

        if (token != null && jwtService.validateToken(token)) {
            UUID userId = jwtService.extractUserId(token);
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId.toString(), null, new ArrayList<>());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        else {
            logger.warn("JWT rejected — token present: {}, path: {}", token != null, request.getRequestURI());
            // ADD THIS:
            if (token != null) {
                logger.warn("Token value (first 50 chars): {}", token.substring(0, Math.min(50, token.length())));
                try {
                    logger.warn("validateToken result: {}", jwtService.validateToken(token));
                } catch (Exception e) {
                    logger.warn("validateToken threw: {}", e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}

