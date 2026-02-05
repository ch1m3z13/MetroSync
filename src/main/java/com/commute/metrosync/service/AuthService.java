package com.commute.metrosync.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.entity.Wallet;
import com.commute.metrosync.dto.request.LoginRequest;
import com.commute.metrosync.dto.request.RegisterRequest;
import com.commute.metrosync.dto.request.VerifyOtpRequest;
import com.commute.metrosync.dto.response.AuthResponse;
import com.commute.metrosync.exception.BusinessException;
import com.commute.metrosync.repository.UserRepository;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class AuthService {

    @Inject
    UserRepository userRepository;

    @Inject
    OtpService otpService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if phone number already exists
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()) != null) {
            throw new BusinessException("Phone number already registered");
        }

        // Generate and send OTP
        String otp = otpService.generateOtp(request.getPhoneNumber(), "REGISTRATION");
        
        // In dev mode, return OTP in response (remove in production)
        return AuthResponse.builder()
            .message("OTP sent to " + request.getPhoneNumber())
            .requiresOtp(true)
            .otp(otp) // Remove in production
            .build();
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        // Verify OTP
        if (!otpService.verifyOtp(request.getPhoneNumber(), request.getOtp())) {
            throw new BusinessException("Invalid or expired OTP");
        }

        // Check if user exists (Login scenario) or Create new (Registration)
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber());
        
        if (user == null) {
            // Registration flow
            user = new User();
            user.setPhoneNumber(request.getPhoneNumber());
            user.setPhoneVerified(true);
            user.setRole(request.getRole() != null ? request.getRole() : User.UserRole.RIDER);
            user.setStatus(User.UserStatus.ACTIVE);
            
            if (request.getPassword() != null) {
                user.setPasswordHash(BcryptUtil.bcryptHash(request.getPassword()));
            }

            userRepository.persist(user);

            // Create wallet
            Wallet wallet = new Wallet();
            wallet.setUser(user);
            wallet.persist();
        }

        // Generate JWT token
        String token = generateToken(user);

        return AuthResponse.builder()
            .token(token)
            .userId(user.id)
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole().name())
            .verified(user.isFullyVerified())
            .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber());
        
        if (user == null) {
            throw new BusinessException("User not found");
        }

        if (request.getPassword() != null) {
            if (user.getPasswordHash() == null || 
                !BcryptUtil.matches(request.getPassword(), user.getPasswordHash())) {
                throw new BusinessException("Invalid credentials");
            }
        } else {
            // OTP-based login
            String otp = otpService.generateOtp(request.getPhoneNumber(), "LOGIN");
            return AuthResponse.builder()
                .message("OTP sent to " + request.getPhoneNumber())
                .requiresOtp(true)
                .otp(otp) // Remove in production
                .build();
        }

        String token = generateToken(user);

        return AuthResponse.builder()
            .token(token)
            .userId(user.id)
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole().name())
            .verified(user.isFullyVerified())
            .build();
    }

    private String generateToken(User user) {
        Set<String> roles = new HashSet<>();
        roles.add(user.getRole().name());

        return Jwt.issuer("https://commuteng.com")
            .upn(user.getPhoneNumber())
            .subject(user.id.toString())
            .groups(roles)
            .claim("userId", user.id)
            .claim("role", user.getRole().name())
            .claim("verified", user.isFullyVerified())
            .expiresIn(Duration.ofDays(1))
            .sign();
    }
}
