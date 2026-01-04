package com.commute.metrosync.service;

import com.commute.metrosync.entity.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;

@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "jwt.access.token.duration", defaultValue = "86400")
    long accessTokenDuration;

    @ConfigProperty(name = "jwt.refresh.token.duration", defaultValue = "604800")
    long refreshTokenDuration;

    /**
     * Generates a short-lived Access Token with User Roles
     */
    public String generateAccessToken(User user) {
        Set<String> roles = new HashSet<>();
        
        // FIX: Split the comma-separated roles string into individual roles
        if (user.getRole() != null && !user.getRole().isEmpty()) {
            String[] roleArray = user.getRole().split(",");
            for (String role : roleArray) {
                roles.add(role.trim());
            }
        } else {
            // Fallback default
            roles.add("RIDER");
        }

        return Jwt.issuer(issuer)
                .upn(user.getUsername())
                .subject(user.getId().toString())
                .groups(roles) // Now contains ["DRIVER", "RIDER"] instead of ["DRIVER,RIDER"]
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .claim("type", "access")
                .expiresIn(accessTokenDuration)
                .sign();
    }

    /**
     * Generates a long-lived Refresh Token (No roles needed usually)
     */
    public String generateRefreshToken(User user) {
        return Jwt.issuer(issuer)
                .upn(user.getUsername())
                .subject(user.getId().toString())
                .claim("type", "refresh")
                .expiresIn(refreshTokenDuration)
                .sign();
    }
}