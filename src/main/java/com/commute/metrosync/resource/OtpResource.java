package com.commute.metrosync.resource;

import com.commute.metrosync.dto.request.VerifyOtpRequest;
import com.commute.metrosync.dto.response.ApiResponse;
import com.commute.metrosync.entity.OtpToken;
import com.commute.metrosync.service.TermiiService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * OTP Resource - API endpoints for OTP operations
 */
@Path("/api/otp")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OtpResource {

    private static final Logger LOG = Logger.getLogger(OtpResource.class);

    @Inject
    TermiiService termiiService;

    /**
     * Send OTP to phone number
     * POST /api/otp/send
     */
    @POST
    @Path("/send")
    public ApiResponse<Map<String, Object>> sendOtp(
        @Valid Map<String, String> request,
        @Context SecurityContext securityContext,
        @HeaderParam("User-Agent") String userAgent,
        @HeaderParam("X-Forwarded-For") String ipAddress
    ) {
        try {
            String phoneNumber = request.get("phoneNumber");
            String purposeStr = request.get("purpose");
            String deviceId = request.get("deviceId");

            if (phoneNumber == null || purposeStr == null) {
                return ApiResponse.error("Phone number and purpose are required");
            }

            OtpToken.OtpPurpose purpose = OtpToken.OtpPurpose.valueOf(purposeStr.toUpperCase());

            TermiiService.OtpSendResult result = termiiService.sendOtp(
                phoneNumber,
                null, // User will be null for registration
                purpose,
                ipAddress,
                userAgent,
                deviceId
            );

            if (result.success) {
                return ApiResponse.success(
                    result.message,
                    Map.of(
                        "otpTokenId", result.otpTokenId.toString(),
                        "expiresIn", "10 minutes"
                    )
                );
            } else {
                return ApiResponse.error(result.message);
            }
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("Invalid OTP purpose");
        } catch (Exception e) {
            LOG.error("Error sending OTP", e);
            return ApiResponse.error("Failed to send OTP");
        }
    }

    /**
     * Verify OTP code
     * POST /api/otp/verify
     */
    @POST
    @Path("/verify")
    public ApiResponse<Map<String, Object>> verifyOtp(@Valid VerifyOtpRequest request) {
        try {
            OtpToken.OtpPurpose purpose = OtpToken.OtpPurpose.valueOf(request.purpose.toUpperCase());

            TermiiService.OtpVerificationResult result = termiiService.verifyOtp(
                request.phoneNumber,
                request.code,
                purpose
            );

            if (result.isValid) {
                return ApiResponse.success(
                    result.message,
                    Map.of(
                        "verified", true,
                        "phoneNumber", request.phoneNumber
                    )
                );
            } else {
                return ApiResponse.error(result.message);
            }
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("Invalid OTP purpose");
        } catch (Exception e) {
            LOG.error("Error verifying OTP", e);
            return ApiResponse.error("Failed to verify OTP");
        }
    }

    /**
     * Resend OTP
     * POST /api/otp/resend
     */
    @POST
    @Path("/resend")
    public ApiResponse<Map<String, Object>> resendOtp(
        @Valid Map<String, String> request,
        @HeaderParam("User-Agent") String userAgent,
        @HeaderParam("X-Forwarded-For") String ipAddress
    ) {
        // Same logic as send, but could add additional rate limiting
        return sendOtp(request, null, userAgent, ipAddress);
    }
}