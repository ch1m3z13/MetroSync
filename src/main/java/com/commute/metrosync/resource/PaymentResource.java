package com.commute.metrosync.resource;

import com.commute.metrosync.dto.request.WalletTopUpRequest;
import com.commute.metrosync.dto.request.WithdrawalRequest;
import com.commute.metrosync.dto.response.ApiResponse;
import com.commute.metrosync.dto.response.PaymentInitResponse;
import com.commute.metrosync.service.PaystackService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;

/**
 * Payment Resource - API endpoints for payment operations
 */
@Path("/api/payment")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    private static final Logger LOG = Logger.getLogger(PaymentResource.class);

    @Inject
    PaystackService paystackService;

    @ConfigProperty(name = "payment.callback.url")
    String defaultCallbackUrl;

    /**
     * Initialize wallet top-up
     * POST /api/payment/topup
     */
    @POST
    @Path("/topup")
    @RolesAllowed({"RIDER", "DRIVER"})
    public ApiResponse<PaymentInitResponse> initializeTopUp(
        @Valid WalletTopUpRequest request,
        @Context SecurityContext securityContext
    ) {
        try {
            // Get user ID from security context
            String username = securityContext.getUserPrincipal().getName();
            UUID userId = UUID.fromString(username); // In production, look up user by username

            String callbackUrl = request.callbackUrl != null ? request.callbackUrl : defaultCallbackUrl;

            PaystackService.PaymentInitResult result = paystackService.initializePayment(
                userId,
                request.amount,
                request.email,
                callbackUrl
            );

            if (result.success) {
                PaymentInitResponse response = new PaymentInitResponse(
                    result.authorizationUrl,
                    result.accessCode,
                    null,
                    result.message
                );
                return ApiResponse.success("Payment initialized", response);
            } else {
                return ApiResponse.error(result.message);
            }
        } catch (Exception e) {
            LOG.error("Error initializing top-up", e);
            return ApiResponse.error("Failed to initialize payment");
        }
    }

    /**
     * Initialize withdrawal
     * POST /api/payment/withdraw
     */
    @POST
    @Path("/withdraw")
    @RolesAllowed({"RIDER", "DRIVER"})
    public ApiResponse<Map<String, String>> initializeWithdrawal(
        @Valid WithdrawalRequest request,
        @Context SecurityContext securityContext
    ) {
        try {
            String username = securityContext.getUserPrincipal().getName();
            UUID userId = UUID.fromString(username);

            PaystackService.WithdrawalInitResult result = paystackService.initializeWithdrawal(
                userId,
                request.amount,
                request.bankCode,
                request.accountNumber,
                request.accountName
            );

            if (result.success) {
                return ApiResponse.success(
                    result.message,
                    Map.of("reference", result.reference)
                );
            } else {
                return ApiResponse.error(result.message);
            }
        } catch (Exception e) {
            LOG.error("Error initializing withdrawal", e);
            return ApiResponse.error("Failed to initialize withdrawal");
        }
    }

    /**
     * Paystack webhook handler
     * POST /api/payment/webhook
     */
    @POST
    @Path("/webhook")
    public ApiResponse<String> handleWebhook(
        Map<String, Object> event,
        @HeaderParam("x-paystack-signature") String signature
    ) {
        try {
            // Verify webhook signature
            // In production, you'd stringify the entire request body
            String payload = event.toString();
            boolean isValid = paystackService.verifyWebhookSignature(payload, signature);

            if (!isValid) {
                LOG.warn("Invalid webhook signature");
                return ApiResponse.error("Invalid signature");
            }

            // Process webhook
            PaystackService.WebhookProcessResult result = paystackService.processWebhook(event);

            if (result.success) {
                return ApiResponse.success(result.message, "Processed");
            } else {
                return ApiResponse.error(result.message);
            }
        } catch (Exception e) {
            LOG.error("Error processing webhook", e);
            return ApiResponse.error("Webhook processing failed");
        }
    }

    /**
     * Payment callback (redirect after payment)
     * GET /api/payment/callback
     */
    @GET
    @Path("/callback")
    public String handleCallback(
        @QueryParam("reference") String reference,
        @QueryParam("status") String status
    ) {
        // In production, redirect to mobile app deep link or web page
        if ("success".equals(status)) {
            return "<html><body><h1>Payment Successful!</h1><p>Reference: " + reference + "</p></body></html>";
        } else {
            return "<html><body><h1>Payment Failed</h1><p>Please try again.</p></body></html>";
        }
    }
}