package com.commute.metrosync.service;

import io.smallrye.jwt.build.Jwt;
import com.commute.metrosync.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class TokenService {
    
    // ✅ Renamed to match application.properties
    @ConfigProperty(name = "jwt.access.token.duration", defaultValue = "86400")
    Long tokenDuration;
    
    /**
     * Generate JWT token for user
     */
    public String generateToken(User user) {
        Set<String> roles = new HashSet<>();
        roles.add(user.getRole());
        
        return Jwt.issuer("your-app-issuer")
                .upn(user.getUsername())
                .subject(user.getId().toString())
                .groups(roles)
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .expiresIn(tokenDuration)
                .sign();
    }
}