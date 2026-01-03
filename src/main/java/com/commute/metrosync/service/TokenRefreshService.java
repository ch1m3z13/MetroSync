package com.commute.metrosync.service;

import io.smallrye.jwt.build.Jwt;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class TokenRefreshService {
    
    @ConfigProperty(name = "jwt.duration", defaultValue = "86400") // 24 hours
    Long accessTokenDuration;
    
    @ConfigProperty(name = "jwt.refresh.duration", defaultValue = "604800") // 7 days
    Long refreshTokenDuration;
    
    @Inject
    UserRepository userRepository;
    
    /**
     * Generate access token (short-lived)
     */
    public String generateAccessToken(User user) {
        Set<String> roles = new HashSet<>();
        roles.add(user.getRole());
        
        return Jwt.issuer("your-app-issuer")
                .upn(user.getUsername())
                .subject(user.getId().toString())
                .groups(roles)
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .claim("type", "access")
                .expiresIn(accessTokenDuration)
                .sign();
    }
    
    /**
     * Generate refresh token (long-lived)
     */
    public String generateRefreshToken(User user) {
        return Jwt.issuer("your-app-issuer")
                .subject(user.getId().toString())
                .claim("type", "refresh")
                .expiresIn(refreshTokenDuration)
                .sign();
    }
    
    /**
     * Refresh access token using valid refresh token
     */
    public TokenPair refreshAccessToken(JsonWebToken refreshToken) {
        // Validate it's a refresh token
        String tokenType = refreshToken.getClaim("type");
        if (!"refresh".equals(tokenType)) {
            throw new IllegalArgumentException("Invalid token type");
        }
        
        // Get user from token
        UUID userId = UUID.fromString(refreshToken.getSubject());
        User user = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // Generate new tokens
        String newAccessToken = generateAccessToken(user);
        String newRefreshToken = generateRefreshToken(user);
        
        return new TokenPair(newAccessToken, newRefreshToken);
    }
    
    public record TokenPair(String accessToken, String refreshToken) {}
}