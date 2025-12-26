package com.commute.metrosync.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasswordService {
    
    /**
     * Hash a plain text password using BCrypt
     */
    public String hashPassword(String plainPassword) {
        return BcryptUtil.bcryptHash(plainPassword);
    }
    
    /**
     * Verify a plain text password against a hashed password
     */
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        return BcryptUtil.matches(plainPassword, hashedPassword);
    }
}