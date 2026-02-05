package com.commute.metrosync.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import com.commute.metrosync.entity.OtpToken;
import com.commute.metrosync.util.PinGenerator;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@ApplicationScoped
@Slf4j
public class OtpService {

    @Transactional
    public String generateOtp(String phoneNumber, String purpose) {
        // Invalidate existing tokens for this purpose
        OtpToken.update("verified = true WHERE phoneNumber = ?1 AND purpose = ?2", 
                       phoneNumber, purpose);

        String code = PinGenerator.generateOtp(6);
        
        OtpToken token = new OtpToken();
        token.setPhoneNumber(phoneNumber);
        token.setOtpCode(code);
        token.setPurpose(purpose);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.persist();

        // In a real production environment, integrate SMS provider (e.g., Termii/Twilio) here
        log.info("OTP generated for {}: {}", phoneNumber, code);
        
        return code;
    }

    @Transactional
    public boolean verifyOtp(String phoneNumber, String code) {
        OtpToken token = OtpToken.find("phoneNumber = ?1 AND otpCode = ?2 AND verified = false", 
                                     phoneNumber, code).firstResult();

        if (token == null) {
            return false;
        }

        if (token.isExpired()) {
            return false;
        }

        token.setVerified(true);
        return true;
    }
}