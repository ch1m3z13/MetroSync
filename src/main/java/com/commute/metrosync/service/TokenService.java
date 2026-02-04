package com.commute.metrosync.service;

import com.commute.metrosync.entity.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * JWT Token Generation Service
 * 
 * Token Structure (as per specification):
 * {
 *   "userId": "uuid",
 *   "email": "user@example.com",
 *   "roles": ["PASSENGER", "DRIVER"],
 *   "iat": 1234567890,
 *   "exp": 1234571490
 * }
 * 
 * Token Requirements:
 * - Expiration: 24 hours (configurable)
 * - Algorithm: HS256 (default) or RS256
 * - Include user roles for authorization
 * - Refresh token optional but recommended
 */
@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    /**
     * Access token duration in seconds (default: 24 hours)
     */
    @ConfigProperty(name = "jwt.access.token.duration", defaultValue = "86400")
    long accessTokenDuration;

    /**
     * Refresh token duration in seconds (default: 7 days)
     */
    @ConfigProperty(name = "jwt.refresh.token.duration", defaultValue = "604800")
    long refreshTokenDuration;

    /**
     * Generate Access Token
     * 
     * Generates a short-lived JWT token with user information and roles.
     * 
     * Token Payload:
     * - sub: User ID (subject)
     * - upn: Username/email (user principal name)
     * - userId: User ID (custom claim for specification compliance)
     * - email: User email
     * - fullName: User's full name
     * - roles/groups: User roles as array (PASSENGER, DRIVER, etc.)
     * - type: "access" (to distinguish from refresh tokens)
     * - iat: Issued at timestamp (automatic)
     * - exp: Expiration timestamp (automatic)
     * 
     * @param user The user to generate token for
     * @return JWT access token string
     */
    public String generateAccessToken(User user) {
        // Parse roles from comma-separated string into Set
        Set<String> roles = parseRoles(user.getRoles());
        
        return Jwt.issuer(issuer)
                // Standard JWT claims
                .upn(user.getEmail())                    // User Principal Name (email)
                .subject(user.getId().toString())        // Subject (user ID)
                .groups(roles)                           // Groups/Roles (Quarkus standard)
                
                // Custom claims for specification compliance
                .claim("userId", user.getId().toString())    // Explicit userId claim
                .claim("email", user.getEmail())             // Email claim
                .claim("roles", roles)                       // Roles as array
                .claim("fullName", user.getFullName())       // User's full name
                .claim("type", "access")                     // Token type
                
                // Token expiration
                .expiresIn(accessTokenDuration)          // Expires in 24 hours (configurable)
                
                // Sign and generate
                .sign();
    }

    /**
     * Generate Refresh Token
     * 
     * Generates a long-lived token that can be used to obtain new access tokens
     * without requiring the user to log in again.
     * 
     * Refresh tokens contain minimal information for security:
     * - sub: User ID
     * - upn: Username/email
     * - type: "refresh"
     * - iat: Issued at
     * - exp: Expiration (7 days by default)
     * 
     * @param user The user to generate refresh token for
     * @return JWT refresh token string
     */
    public String generateRefreshToken(User user) {
        return Jwt.issuer(issuer)
                .upn(user.getEmail())
                .subject(user.getId().toString())
                .claim("type", "refresh")           // Mark as refresh token
                .claim("userId", user.getId().toString())
                .expiresIn(refreshTokenDuration)    // Expires in 7 days (configurable)
                .sign();
    }

    /**
     * Parse roles from comma-separated string into Set
     * 
     * Input: "PASSENGER,DRIVER" or "DRIVER" or "PASSENGER"
     * Output: Set of role strings
     * 
     * @param rolesString Comma-separated roles from database
     * @return Set of role strings
     */
    private Set<String> parseRoles(String rolesString) {
        Set<String> roles = new HashSet<>();
        
        if (rolesString != null && !rolesString.isEmpty()) {
            String[] roleArray = rolesString.split(",");
            for (String role : roleArray) {
                roles.add(role.trim());
            }
        } else {
            // Default role if none specified
            roles.add("PASSENGER");
        }
        
        return roles;
    }
}