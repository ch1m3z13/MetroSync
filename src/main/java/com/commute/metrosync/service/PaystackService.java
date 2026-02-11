package com.commute.ng.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Paystack Payment Service
 * Handles all Paystack API interactions for CommuteNG
 * 
 * API Documentation: https://paystack.com/docs/api/
 */
@Service
public class PaystackService {
    
    private static final Logger logger = LoggerFactory.getLogger(PaystackService.class);
    private static final String PAYSTACK_API_URL = "https://api.paystack.co";
    
    @Value("${paystack.secret.key}")
    private String paystackSecretKey;
    
    @Value("${paystack.public.key}")
    private String paystackPublicKey;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public PaystackService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Initialize a payment transaction
     * 
     * @param email User's email address
     * @param amount Amount in kobo (NGN minor unit: 1 NGN = 100 kobo)
     * @param reference Unique transaction reference
     * @param metadata Additional data to attach to transaction
     * @return PaystackInitializeResponse containing authorization_url and access_code
     */
    public PaystackInitializeResponse initializeTransaction(
            String email, 
            Long amount, 
            String reference, 
            Map<String, Object> metadata) {
        
        try {
            String url = PAYSTACK_API_URL + "/transaction/initialize";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("email", email);
            requestBody.put("amount", amount); // Amount in kobo
            requestBody.put("reference", reference);
            requestBody.put("currency", "NGN");
            requestBody.put("channels", Arrays.asList("card", "bank", "ussd", "qr", "bank_transfer"));
            
            if (metadata != null && !metadata.isEmpty()) {
                requestBody.put("metadata", metadata);
            }
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            logger.info("Initializing Paystack transaction: reference={}, amount={} kobo", reference, amount);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            if (jsonResponse.get("status").asBoolean()) {
                JsonNode data = jsonResponse.get("data");
                return new PaystackInitializeResponse(
                    true,
                    data.get("authorization_url").asText(),
                    data.get("access_code").asText(),
                    reference
                );
            } else {
                String message = jsonResponse.get("message").asText();
                logger.error("Paystack initialization failed: {}", message);
                return new PaystackInitializeResponse(false, null, null, reference, message);
            }
            
        } catch (Exception e) {
            logger.error("Error initializing Paystack transaction", e);
            return new PaystackInitializeResponse(false, null, null, reference, e.getMessage());
        }
    }
    
    /**
     * Verify a transaction
     * 
     * @param reference Transaction reference to verify
     * @return PaystackVerifyResponse with transaction details
     */
    public PaystackVerifyResponse verifyTransaction(String reference) {
        try {
            String url = PAYSTACK_API_URL + "/transaction/verify/" + reference;
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            logger.info("Verifying Paystack transaction: reference={}", reference);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            if (jsonResponse.get("status").asBoolean()) {
                JsonNode data = jsonResponse.get("data");
                
                return PaystackVerifyResponse.builder()
                    .success(true)
                    .reference(data.get("reference").asText())
                    .amount(data.get("amount").asLong())
                    .status(data.get("status").asText())
                    .paidAt(parseDateTime(data.get("paid_at")))
                    .channel(data.get("channel").asText())
                    .currency(data.get("currency").asText())
                    .ipAddress(data.path("ip_address").asText())
                    .metadata(parseMetadata(data.get("metadata")))
                    .gatewayResponse(data.path("gateway_response").asText())
                    .build();
            } else {
                String message = jsonResponse.get("message").asText();
                logger.error("Paystack verification failed: {}", message);
                return PaystackVerifyResponse.builder()
                    .success(false)
                    .reference(reference)
                    .errorMessage(message)
                    .build();
            }
            
        } catch (Exception e) {
            logger.error("Error verifying Paystack transaction", e);
            return PaystackVerifyResponse.builder()
                .success(false)
                .reference(reference)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    /**
     * Create a transfer recipient
     * Used to send money to driver's bank account
     * 
     * @param type Account type: "nuban" for Nigerian bank accounts
     * @param name Account holder name
     * @param accountNumber Bank account number
     * @param bankCode Bank code (e.g., "058" for GTBank)
     * @return Recipient code for future transfers
     */
    public PaystackRecipientResponse createTransferRecipient(
            String type, 
            String name, 
            String accountNumber, 
            String bankCode) {
        
        try {
            String url = PAYSTACK_API_URL + "/transferrecipient";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("type", type); // "nuban" for Nigerian accounts
            requestBody.put("name", name);
            requestBody.put("account_number", accountNumber);
            requestBody.put("bank_code", bankCode);
            requestBody.put("currency", "NGN");
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            logger.info("Creating Paystack transfer recipient: name={}, bank={}", name, bankCode);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            if (jsonResponse.get("status").asBoolean()) {
                JsonNode data = jsonResponse.get("data");
                return new PaystackRecipientResponse(
                    true,
                    data.get("recipient_code").asText(),
                    data.get("type").asText()
                );
            } else {
                String message = jsonResponse.get("message").asText();
                logger.error("Paystack recipient creation failed: {}", message);
                return new PaystackRecipientResponse(false, null, null, message);
            }
            
        } catch (Exception e) {
            logger.error("Error creating Paystack transfer recipient", e);
            return new PaystackRecipientResponse(false, null, null, e.getMessage());
        }
    }
    
    /**
     * Initiate a transfer to a recipient
     * Used for driver payouts
     * 
     * @param amount Amount in kobo
     * @param recipientCode Recipient code from createTransferRecipient
     * @param reason Transfer description
     * @param reference Unique transfer reference
     * @return Transfer response with status and details
     */
    public PaystackTransferResponse initiateTransfer(
            Long amount, 
            String recipientCode, 
            String reason, 
            String reference) {
        
        try {
            String url = PAYSTACK_API_URL + "/transfer";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("source", "balance"); // Transfer from Paystack balance
            requestBody.put("amount", amount); // Amount in kobo
            requestBody.put("recipient", recipientCode);
            requestBody.put("reason", reason);
            requestBody.put("reference", reference);
            requestBody.put("currency", "NGN");
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            logger.info("Initiating Paystack transfer: reference={}, amount={} kobo", reference, amount);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            if (jsonResponse.get("status").asBoolean()) {
                JsonNode data = jsonResponse.get("data");
                return PaystackTransferResponse.builder()
                    .success(true)
                    .transferCode(data.get("transfer_code").asText())
                    .reference(data.get("reference").asText())
                    .status(data.get("status").asText())
                    .amount(data.get("amount").asLong())
                    .recipientCode(data.get("recipient").asText())
                    .build();
            } else {
                String message = jsonResponse.get("message").asText();
                logger.error("Paystack transfer failed: {}", message);
                return PaystackTransferResponse.builder()
                    .success(false)
                    .reference(reference)
                    .errorMessage(message)
                    .build();
            }
            
        } catch (Exception e) {
            logger.error("Error initiating Paystack transfer", e);
            return PaystackTransferResponse.builder()
                .success(false)
                .reference(reference)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    /**
     * Verify transfer status
     * 
     * @param reference Transfer reference
     * @return Transfer verification response
     */
    public PaystackTransferResponse verifyTransfer(String reference) {
        try {
            String url = PAYSTACK_API_URL + "/transfer/verify/" + reference;
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            logger.info("Verifying Paystack transfer: reference={}", reference);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            if (jsonResponse.get("status").asBoolean()) {
                JsonNode data = jsonResponse.get("data");
                return PaystackTransferResponse.builder()
                    .success(true)
                    .transferCode(data.get("transfer_code").asText())
                    .reference(data.get("reference").asText())
                    .status(data.get("status").asText())
                    .amount(data.get("amount").asLong())
                    .build();
            } else {
                String message = jsonResponse.get("message").asText();
                logger.error("Paystack transfer verification failed: {}", message);
                return PaystackTransferResponse.builder()
                    .success(false)
                    .reference(reference)
                    .errorMessage(message)
                    .build();
            }
            
        } catch (Exception e) {
            logger.error("Error verifying Paystack transfer", e);
            return PaystackTransferResponse.builder()
                .success(false)
                .reference(reference)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    /**
     * Validate webhook signature
     * Ensures webhook requests are genuinely from Paystack
     * 
     * @param payload Request body as string
     * @param signature X-Paystack-Signature header value
     * @return true if signature is valid
     */
    public boolean validateWebhookSignature(String payload, String signature) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                paystackSecretKey.getBytes(), 
                "HmacSHA512"
            );
            mac.init(secretKey);
            
            byte[] hash = mac.doFinal(payload.getBytes());
            String computedSignature = bytesToHex(hash);
            
            return computedSignature.equals(signature);
        } catch (Exception e) {
            logger.error("Error validating Paystack webhook signature", e);
            return false;
        }
    }
    
    /**
     * List Nigerian banks
     * Get list of banks for bank account verification
     * 
     * @return List of Nigerian banks
     */
    public List<PaystackBank> listBanks() {
        try {
            String url = PAYSTACK_API_URL + "/bank?country=nigeria";
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            if (jsonResponse.get("status").asBoolean()) {
                JsonNode data = jsonResponse.get("data");
                List<PaystackBank> banks = new ArrayList<>();
                
                for (JsonNode bank : data) {
                    banks.add(new PaystackBank(
                        bank.get("id").asLong(),
                        bank.get("name").asText(),
                        bank.get("code").asText(),
                        bank.get("slug").asText()
                    ));
                }
                
                return banks;
            } else {
                logger.error("Failed to fetch banks from Paystack");
                return Collections.emptyList();
            }
            
        } catch (Exception e) {
            logger.error("Error fetching banks from Paystack", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Resolve account number
     * Verify bank account details before creating transfer recipient
     * 
     * @param accountNumber Bank account number
     * @param bankCode Bank code
     * @return Account name if valid, null otherwise
     */
    public String resolveAccountNumber(String accountNumber, String bankCode) {
        try {
            String url = PAYSTACK_API_URL + "/bank/resolve?account_number=" + accountNumber + "&bank_code=" + bankCode;
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            
            if (jsonResponse.get("status").asBoolean()) {
                JsonNode data = jsonResponse.get("data");
                return data.get("account_name").asText();
            } else {
                logger.warn("Failed to resolve account number: {}", accountNumber);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error resolving account number", e);
            return null;
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + paystackSecretKey);
        return headers;
    }
    
    private LocalDateTime parseDateTime(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return LocalDateTime.parse(node.asText(), DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
    
    private Map<String, Object> parseMetadata(JsonNode node) {
        if (node == null || node.isNull()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    // ==================== RESPONSE CLASSES ====================
    
    public static class PaystackInitializeResponse {
        private boolean success;
        private String authorizationUrl;
        private String accessCode;
        private String reference;
        private String errorMessage;
        
        public PaystackInitializeResponse(boolean success, String authorizationUrl, String accessCode, String reference) {
            this.success = success;
            this.authorizationUrl = authorizationUrl;
            this.accessCode = accessCode;
            this.reference = reference;
        }
        
        public PaystackInitializeResponse(boolean success, String authorizationUrl, String accessCode, String reference, String errorMessage) {
            this.success = success;
            this.authorizationUrl = authorizationUrl;
            this.accessCode = accessCode;
            this.reference = reference;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getAuthorizationUrl() { return authorizationUrl; }
        public String getAccessCode() { return accessCode; }
        public String getReference() { return reference; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class PaystackVerifyResponse {
        private boolean success;
        private String reference;
        private Long amount;
        private String status;
        private LocalDateTime paidAt;
        private String channel;
        private String currency;
        private String ipAddress;
        private Map<String, Object> metadata;
        private String gatewayResponse;
        private String errorMessage;
        
        private PaystackVerifyResponse(Builder builder) {
            this.success = builder.success;
            this.reference = builder.reference;
            this.amount = builder.amount;
            this.status = builder.status;
            this.paidAt = builder.paidAt;
            this.channel = builder.channel;
            this.currency = builder.currency;
            this.ipAddress = builder.ipAddress;
            this.metadata = builder.metadata;
            this.gatewayResponse = builder.gatewayResponse;
            this.errorMessage = builder.errorMessage;
        }
        
        public static Builder builder() { return new Builder(); }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getReference() { return reference; }
        public Long getAmount() { return amount; }
        public String getStatus() { return status; }
        public LocalDateTime getPaidAt() { return paidAt; }
        public String getChannel() { return channel; }
        public String getCurrency() { return currency; }
        public String getIpAddress() { return ipAddress; }
        public Map<String, Object> getMetadata() { return metadata; }
        public String getGatewayResponse() { return gatewayResponse; }
        public String getErrorMessage() { return errorMessage; }
        
        public static class Builder {
            private boolean success;
            private String reference;
            private Long amount;
            private String status;
            private LocalDateTime paidAt;
            private String channel;
            private String currency;
            private String ipAddress;
            private Map<String, Object> metadata;
            private String gatewayResponse;
            private String errorMessage;
            
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder reference(String reference) { this.reference = reference; return this; }
            public Builder amount(Long amount) { this.amount = amount; return this; }
            public Builder status(String status) { this.status = status; return this; }
            public Builder paidAt(LocalDateTime paidAt) { this.paidAt = paidAt; return this; }
            public Builder channel(String channel) { this.channel = channel; return this; }
            public Builder currency(String currency) { this.currency = currency; return this; }
            public Builder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
            public Builder gatewayResponse(String gatewayResponse) { this.gatewayResponse = gatewayResponse; return this; }
            public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
            
            public PaystackVerifyResponse build() { return new PaystackVerifyResponse(this); }
        }
    }
    
    public static class PaystackRecipientResponse {
        private boolean success;
        private String recipientCode;
        private String type;
        private String errorMessage;
        
        public PaystackRecipientResponse(boolean success, String recipientCode, String type) {
            this.success = success;
            this.recipientCode = recipientCode;
            this.type = type;
        }
        
        public PaystackRecipientResponse(boolean success, String recipientCode, String type, String errorMessage) {
            this.success = success;
            this.recipientCode = recipientCode;
            this.type = type;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getRecipientCode() { return recipientCode; }
        public String getType() { return type; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class PaystackTransferResponse {
        private boolean success;
        private String transferCode;
        private String reference;
        private String status;
        private Long amount;
        private String recipientCode;
        private String errorMessage;
        
        private PaystackTransferResponse(Builder builder) {
            this.success = builder.success;
            this.transferCode = builder.transferCode;
            this.reference = builder.reference;
            this.status = builder.status;
            this.amount = builder.amount;
            this.recipientCode = builder.recipientCode;
            this.errorMessage = builder.errorMessage;
        }
        
        public static Builder builder() { return new Builder(); }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getTransferCode() { return transferCode; }
        public String getReference() { return reference; }
        public String getStatus() { return status; }
        public Long getAmount() { return amount; }
        public String getRecipientCode() { return recipientCode; }
        public String getErrorMessage() { return errorMessage; }
        
        public static class Builder {
            private boolean success;
            private String transferCode;
            private String reference;
            private String status;
            private Long amount;
            private String recipientCode;
            private String errorMessage;
            
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder transferCode(String transferCode) { this.transferCode = transferCode; return this; }
            public Builder reference(String reference) { this.reference = reference; return this; }
            public Builder status(String status) { this.status = status; return this; }
            public Builder amount(Long amount) { this.amount = amount; return this; }
            public Builder recipientCode(String recipientCode) { this.recipientCode = recipientCode; return this; }
            public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
            
            public PaystackTransferResponse build() { return new PaystackTransferResponse(this); }
        }
    }
    
    public static class PaystackBank {
        private Long id;
        private String name;
        private String code;
        private String slug;
        
        public PaystackBank(Long id, String name, String code, String slug) {
            this.id = id;
            this.name = name;
            this.code = code;
            this.slug = slug;
        }
        
        // Getters
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getCode() { return code; }
        public String getSlug() { return slug; }
    }
}