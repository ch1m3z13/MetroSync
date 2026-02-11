package com.commute.metrosync.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentInitResponse {

    public String authorizationUrl;
    public String accessCode;
    public String reference;
    public String message;

    public PaymentInitResponse() {}

    public PaymentInitResponse(String authorizationUrl, String accessCode, String reference, String message) {
        this.authorizationUrl = authorizationUrl;
        this.accessCode = accessCode;
        this.reference = reference;
        this.message = message;
    }
}