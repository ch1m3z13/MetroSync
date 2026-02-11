package com.commute.ng.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Termii SMS Service
 * Handles SMS delivery for OTP and notifications via Termii API
 * 
 * API Documentation: https://developers.termii.com/
 */
@Service
public class TermiiService {
    
    private static final Logger logger = LoggerFactory.getLogger(TermiiService.class);
    private static final String TERMII_API_URL = "https://api.ng.termii.com/api";
    
    @Value("${termii.api.key}")
    private String termiiApiKey;
    
    @Value("${termii.sender.id:CommuteNG}")
    private String senderName; // Must be registered with Termii
    
    @Value("${termii.channel:generic}") // generic, dnd, WhatsApp
    private String defaultChannel;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public TermiiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Send OTP via SMS using Termii messaging API
     * 
     * @param phoneNumber Phone number in E.164 format (e.g., +2348012345678)
     * @param otpCode 6-digit OTP code
     * @return SMS delivery response
     */
    public TermiiSmsResponse sendOtp(String phoneNumber, String otpCode) {
        String message = String.format(
            "Your CommuteNG verification code is: %s. Valid for 10 minutes. Do not share this code.",
            otpCode
        );
        
        return sendSms(phoneNumber, message, "OTP");
    }
    
    /**
     * Send OTP using Termii's Token API (recommended for OTP)
     * Termii will generate and send the OTP, then return the pin_id for verification
     * 
     * @param phoneNumber Phone number in E.164 format
     * @param pinType Type of PIN: NUMERIC (default) or ALPHANUMERIC
     * @param pinLength Length of PIN (4-8 digits, default 6)
     * @param pinPlaceholder Placeholder in message template (e.g., "< 1234 >")
     * @return Token response with pin_id for verification
     */
    public TermiiTokenResponse sendTokenOtp(
            String phoneNumber, 
            String pinType, 
            int pinLength, 
            String pinPlaceholder) {
        
        try {
            String url = TERMII_API_URL + "/sms/otp/send";
            
            String messageTemplate = String.format(
                "Your CommuteNG verification code is %s. Valid for 10 minutes. Do not share this code.",
                pinPlaceholder
            );
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("api_key", termiiApiKey);
            requestBody.put("message_type", pinType != null ? pinType : "NUMERIC");
            requestBody.put("to", phoneNumber);
            requestBody.put("from", senderName);
            requestBody.put("channel", defaultChannel);
            requestBody.put("pin_attempts", 3);
            requestBody.put("pin_time_to_live", 10); // 10 minutes
            requestBody.put("pin_length", pinLength > 0 ? pinLength : 6);
            requestBody.put("pin_placeholder", pinPlaceholder != null ? pinPlaceholder : "< 1234 >");
            requestBody.put("message_text", messageTemplate);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            logger.info("Sending Termii token OTP to: {}", maskPhoneNumber(phoneNumber));
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            String pinId = jsonResponse.path("pinId").asText();
            String status = jsonResponse.path("status").asText();
            
            if ("success".equalsIgnoreCase(status)) {
                logger.info("Termii token OTP sent successfully, pinId={}", pinId);
                return new TermiiTokenResponse(true, pinId, status);
            } else {
                logger.error("Termii token OTP failed: {}", jsonResponse.path("message").asText());
                return new TermiiTokenResponse(false, null, status, jsonResponse.path("message").asText());
            }
            
        } catch (Exception e) {
            logger.error("Error sending Termii token OTP", e);
            return new TermiiTokenResponse(false, null, "ERROR", e.getMessage());
        }
    }
    
    /**
     * Verify OTP sent via Token API
     * 
     * @param pinId Pin ID returned from sendTokenOtp
     * @param pin User-entered PIN code
     * @return Verification result
     */
    public TermiiVerifyResponse verifyTokenOtp(String pinId, String pin) {
        try {
            String url = TERMII_API_URL + "/sms/otp/verify";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("api_key", termiiApiKey);
            requestBody.put("pin_id", pinId);
            requestBody.put("pin", pin);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            logger.info("Verifying Termii token OTP: pinId={}", pinId);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            boolean verified = "Verified".equalsIgnoreCase(jsonResponse.path("verified").asText());
            String msisdn = jsonResponse.path("msisdn").asText();
            
            if (verified) {
                logger.info("Termii token OTP verified successfully");
                return new TermiiVerifyResponse(true, verified, msisdn);
            } else {
                logger.warn("Termii token OTP verification failed");
                return new TermiiVerifyResponse(false, verified, msisdn, "Invalid PIN");
            }
            
        } catch (Exception e) {
            logger.error("Error verifying Termii token OTP", e);
            return new TermiiVerifyResponse(false, false, null, e.getMessage());
        }
    }
    
    /**
     * Send generic SMS message
     * 
     * @param phoneNumber Phone number in E.164 format
     * @param message SMS content (max 160 chars for single SMS)
     * @param messageType Message classification (for logging)
     * @return SMS delivery response
     */
    public TermiiSmsResponse sendSms(String phoneNumber, String message, String messageType) {
        try {
            String url = TERMII_API_URL + "/sms/send";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("to", phoneNumber);
            requestBody.put("from", senderName);
            requestBody.put("sms", message);
            requestBody.put("type", "plain"); // plain text message
            requestBody.put("channel", defaultChannel);
            requestBody.put("api_key", termiiApiKey);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            logger.info("Sending {} SMS to: {}", messageType, maskPhoneNumber(phoneNumber));
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            String messageId = jsonResponse.path("message_id").asText();
            String status = jsonResponse.path("message").asText();
            double balance = jsonResponse.path("balance").asDouble();
            
            if ("Successfully Sent".equalsIgnoreCase(status)) {
                logger.info("SMS sent successfully, messageId={}, balance={}", messageId, balance);
                return new TermiiSmsResponse(true, messageId, status, balance);
            } else {
                logger.error("SMS sending failed: {}", status);
                return new TermiiSmsResponse(false, messageId, status, balance, status);
            }
            
        } catch (Exception e) {
            logger.error("Error sending SMS via Termii", e);
            return new TermiiSmsResponse(false, null, "ERROR", 0.0, e.getMessage());
        }
    }
    
    /**
     * Send bulk SMS to multiple recipients
     * 
     * @param phoneNumbers Array of phone numbers
     * @param message SMS content
     * @return Bulk SMS response
     */
    public TermiiBulkSmsResponse sendBulkSms(String[] phoneNumbers, String message) {
        try {
            String url = TERMII_API_URL + "/sms/send/bulk";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("to", phoneNumbers);
            requestBody.put("from", senderName);
            requestBody.put("sms", message);
            requestBody.put("type", "plain");
            requestBody.put("channel", defaultChannel);
            requestBody.put("api_key", termiiApiKey);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            logger.info("Sending bulk SMS to {} recipients", phoneNumbers.length);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            String messageId = jsonResponse.path("message_id").asText();
            String status = jsonResponse.path("message").asText();
            
            return new TermiiBulkSmsResponse(true, messageId, status, phoneNumbers.length);
            
        } catch (Exception e) {
            logger.error("Error sending bulk SMS via Termii", e);
            return new TermiiBulkSmsResponse(false, null, e.getMessage(), 0);
        }
    }
    
    /**
     * Check account balance
     * 
     * @return Current Termii account balance
     */
    public TermiiBalanceResponse getBalance() {
        try {
            String url = TERMII_API_URL + "/get-balance?api_key=" + termiiApiKey;
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            double balance = jsonResponse.path("balance").asDouble();
            String currency = jsonResponse.path("currency").asText();
            
            logger.info("Termii account balance: {} {}", balance, currency);
            
            return new TermiiBalanceResponse(true, balance, currency);
            
        } catch (Exception e) {
            logger.error("Error fetching Termii balance", e);
            return new TermiiBalanceResponse(false, 0.0, null, e.getMessage());
        }
    }
    
    /**
     * Get SMS delivery status
     * 
     * @param messageId Message ID from send response
     * @return Delivery status
     */
    public TermiiStatusResponse getMessageStatus(String messageId) {
        try {
            String url = TERMII_API_URL + "/sms/inbox?api_key=" + termiiApiKey + "&message_id=" + messageId;
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            if (jsonResponse.isArray() && jsonResponse.size() > 0) {
                JsonNode msg = jsonResponse.get(0);
                String status = msg.path("status").asText();
                
                return new TermiiStatusResponse(true, messageId, status);
            } else {
                return new TermiiStatusResponse(false, messageId, "NOT_FOUND", "Message not found");
            }
            
        } catch (Exception e) {
            logger.error("Error fetching message status from Termii", e);
            return new TermiiStatusResponse(false, messageId, "ERROR", e.getMessage());
        }
    }
    
    /**
     * Send notification SMS for booking events
     * 
     * @param phoneNumber Recipient phone number
     * @param bookingId Booking reference
     * @param eventType Event type (CONFIRMED, CANCELLED, etc.)
     * @return SMS response
     */
    public TermiiSmsResponse sendBookingNotification(
            String phoneNumber, 
            String bookingId, 
            String eventType) {
        
        String message = switch (eventType) {
            case "CONFIRMED" -> String.format(
                "Your CommuteNG ride (Ref: %s) has been confirmed. Your driver will contact you soon.",
                bookingId
            );
            case "CANCELLED" -> String.format(
                "Your CommuteNG ride (Ref: %s) has been cancelled. You will receive a refund within 24 hours.",
                bookingId
            );
            case "DRIVER_ARRIVED" -> String.format(
                "Your driver has arrived for ride %s. Please proceed to the pickup point.",
                bookingId
            );
            case "TRIP_STARTED" -> String.format(
                "Your trip %s has started. Have a safe journey!",
                bookingId
            );
            case "TRIP_COMPLETED" -> String.format(
                "Thank you for using CommuteNG! Please rate your experience for trip %s.",
                bookingId
            );
            default -> String.format(
                "CommuteNG update for booking %s: %s",
                bookingId, eventType
            );
        };
        
        return sendSms(phoneNumber, message, "BOOKING_" + eventType);
    }
    
    // ==================== HELPER METHODS ====================
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return "***";
        }
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
    
    // ==================== RESPONSE CLASSES ====================
    
    public static class TermiiSmsResponse {
        private boolean success;
        private String messageId;
        private String status;
        private double balance;
        private String errorMessage;
        
        public TermiiSmsResponse(boolean success, String messageId, String status, double balance) {
            this.success = success;
            this.messageId = messageId;
            this.status = status;
            this.balance = balance;
        }
        
        public TermiiSmsResponse(boolean success, String messageId, String status, double balance, String errorMessage) {
            this.success = success;
            this.messageId = messageId;
            this.status = status;
            this.balance = balance;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessageId() { return messageId; }
        public String getStatus() { return status; }
        public double getBalance() { return balance; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class TermiiTokenResponse {
        private boolean success;
        private String pinId;
        private String status;
        private String errorMessage;
        
        public TermiiTokenResponse(boolean success, String pinId, String status) {
            this.success = success;
            this.pinId = pinId;
            this.status = status;
        }
        
        public TermiiTokenResponse(boolean success, String pinId, String status, String errorMessage) {
            this.success = success;
            this.pinId = pinId;
            this.status = status;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getPinId() { return pinId; }
        public String getStatus() { return status; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class TermiiVerifyResponse {
        private boolean success;
        private boolean verified;
        private String phoneNumber;
        private String errorMessage;
        
        public TermiiVerifyResponse(boolean success, boolean verified, String phoneNumber) {
            this.success = success;
            this.verified = verified;
            this.phoneNumber = phoneNumber;
        }
        
        public TermiiVerifyResponse(boolean success, boolean verified, String phoneNumber, String errorMessage) {
            this.success = success;
            this.verified = verified;
            this.phoneNumber = phoneNumber;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public boolean isVerified() { return verified; }
        public String getPhoneNumber() { return phoneNumber; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class TermiiBulkSmsResponse {
        private boolean success;
        private String messageId;
        private String status;
        private int recipientCount;
        
        public TermiiBulkSmsResponse(boolean success, String messageId, String status, int recipientCount) {
            this.success = success;
            this.messageId = messageId;
            this.status = status;
            this.recipientCount = recipientCount;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessageId() { return messageId; }
        public String getStatus() { return status; }
        public int getRecipientCount() { return recipientCount; }
    }
    
    public static class TermiiBalanceResponse {
        private boolean success;
        private double balance;
        private String currency;
        private String errorMessage;
        
        public TermiiBalanceResponse(boolean success, double balance, String currency) {
            this.success = success;
            this.balance = balance;
            this.currency = currency;
        }
        
        public TermiiBalanceResponse(boolean success, double balance, String currency, String errorMessage) {
            this.success = success;
            this.balance = balance;
            this.currency = currency;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public double getBalance() { return balance; }
        public String getCurrency() { return currency; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class TermiiStatusResponse {
        private boolean success;
        private String messageId;
        private String status;
        private String errorMessage;
        
        public TermiiStatusResponse(boolean success, String messageId, String status) {
            this.success = success;
            this.messageId = messageId;
            this.status = status;
        }
        
        public TermiiStatusResponse(boolean success, String messageId, String status, String errorMessage) {
            this.success = success;
            this.messageId = messageId;
            this.status = status;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessageId() { return messageId; }
        public String getStatus() { return status; }
        public String getErrorMessage() { return errorMessage; }
    }
}