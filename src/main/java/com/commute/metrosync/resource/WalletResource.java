package com.commute.metrosync.resource;

import com.commute.metrosync.dto.*;
import com.commute.metrosync.dto.response.*;
import com.commute.metrosync.service.PaystackService;
import com.commute.metrosync.service.WalletService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Wallet Resource
 * Handles wallet operations including topup, withdrawal, transactions, and payment webhooks
 * Jakarta EE / JAX-RS implementation
 */
@Path("/api/v1/wallet")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Wallet", description = "Wallet and payment endpoints")
@SecurityRequirement(name = "bearerAuth")
public class WalletResource {
    
    private static final Logger logger = Logger.getLogger(WalletResource.class.getName());
    
    @Inject
    private WalletService walletService;
    
    @Inject
    private PaystackService paystackService;
    
    @Context
    private SecurityContext securityContext;
    
    @Context
    private UriInfo uriInfo;
    
    // ==================== WALLET BALANCE ====================
    
    @GET
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get wallet balance", description = "Get current wallet balance and statistics")
    public Response getWallet() {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " requesting wallet balance");
            
            WalletResponse response = walletService.getWalletBalance(userId);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error getting wallet balance: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get wallet balance"))
                .build();
        }
    }
    
    // ==================== WALLET TOPUP ====================
    
    @POST
    @Path("/topup")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Initiate wallet topup", description = "Initialize payment for wallet topup via Paystack")
    public Response initializeTopup(@Valid TopupRequest request) {
        
        try {
            UUID userId = getUserId();
            String email = getUserEmail();
            logger.info("User " + userId + " initiating wallet topup: amount=" + request.getAmount());
            
            // Validate amount
            if (request.getAmount().compareTo(BigDecimal.valueOf(100)) < 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Minimum topup amount is ₦100"))
                    .build();
            }
            
            if (request.getAmount().compareTo(BigDecimal.valueOf(1000000)) > 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Maximum topup amount is ₦1,000,000"))
                    .build();
            }
            
            TopupInitializeResponse response = walletService.initializeTopup(
                userId,
                request.getAmount(),
                email
            );
            
            return Response.ok(ApiResponse.success(response, "Payment initialized successfully")).build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid topup request: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        } catch (Exception e) {
            logger.severe("Error initializing topup: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to initialize payment"))
                .build();
        }
    }
    
    @GET
    @Path("/topup/verify/{reference}")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Verify topup payment", description = "Verify Paystack payment for wallet topup")
    public Response verifyTopup(@PathParam("reference") String reference) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " verifying topup payment: reference=" + reference);
            
            PaymentVerificationResponse response = walletService.verifyTopup(userId, reference);
            
            if (response.isSuccess()) {
                return Response.ok(ApiResponse.success(response, "Payment verified successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getMessage()))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error verifying topup payment: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to verify payment"))
                .build();
        }
    }
    
    // ==================== WALLET WITHDRAWAL ====================
    
    @POST
    @Path("/withdraw")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Request withdrawal", description = "Withdraw funds from wallet to bank account")
    public Response requestWithdrawal(@Valid WithdrawalRequest request) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " requesting withdrawal: amount=" + request.getAmount());
            
            // Validate amount
            if (request.getAmount().compareTo(BigDecimal.valueOf(500)) < 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Minimum withdrawal amount is ₦500"))
                    .build();
            }
            
            WithdrawalResponse response = walletService.requestWithdrawal(userId, request);
            
            if (response.isSuccess()) {
                return Response.ok(ApiResponse.success(response, "Withdrawal initiated successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getMessage()))
                    .build();
            }
            
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid withdrawal request: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        } catch (Exception e) {
            logger.severe("Error processing withdrawal: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to process withdrawal"))
                .build();
        }
    }
    
    // ==================== TRANSACTION HISTORY ====================
    
    @GET
    @Path("/transactions")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get transaction history", description = "Get paginated transaction history with optional filters")
    public Response getTransactions(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) int size,
            @QueryParam("type") String type,
            @QueryParam("category") String category,
            @QueryParam("status") String status,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " requesting transaction history: page=" + page + ", size=" + size);
            
            // Validate pagination
            if (size > 100) {
                size = 100; // Max page size
            }
            
            // Parse dates
            LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : null;
            LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate) : null;
            
            PagedResult<TransactionResponse> transactions = walletService.getTransactions(
                userId, 
                type, 
                category, 
                status,
                start, 
                end, 
                page,
                size
            );
            
            return Response.ok(ApiResponse.success(transactions)).build();
            
        } catch (Exception e) {
            logger.severe("Error getting transaction history: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get transaction history"))
                .build();
        }
    }
    
    @GET
    @Path("/transactions/{transactionId}")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get transaction details", description = "Get detailed information about a specific transaction")
    public Response getTransactionDetails(@PathParam("transactionId") UUID transactionId) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " requesting transaction details: transactionId=" + transactionId);
            
            TransactionDetailResponse response = walletService.getTransactionDetails(userId, transactionId);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("Transaction not found or unauthorized: " + e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ApiResponse.error("Transaction not found"))
                .build();
        } catch (Exception e) {
            logger.severe("Error getting transaction details: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get transaction details"))
                .build();
        }
    }
    
    @GET
    @Path("/transactions/summary")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get transaction summary", description = "Get summary statistics for transactions within a date range")
    public Response getTransactionSummary(
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate) {
        
        try {
            UUID userId = getUserId();
            
            // Default to last 30 days if no dates provided
            LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : LocalDateTime.now().minusDays(30);
            LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate) : LocalDateTime.now();
            
            logger.info("User " + userId + " requesting transaction summary: " + start + " to " + end);
            
            TransactionSummaryResponse response = walletService.getTransactionSummary(userId, start, end);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error getting transaction summary: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get transaction summary"))
                .build();
        }
    }
    
    // ==================== PAYSTACK WEBHOOK ====================
    
    @POST
    @Path("/webhook")
    @PermitAll
    @Operation(summary = "Paystack webhook", description = "Webhook endpoint for Paystack payment notifications")
    public Response paystackWebhook(
            @Context HttpServletRequest request,
            String payload) {
        
        try {
            // Get signature from header
            String signature = request.getHeader("X-Paystack-Signature");
            
            // Validate webhook signature
            if (signature == null || !paystackService.validateWebhookSignature(payload, signature)) {
                logger.warning("Invalid Paystack webhook signature");
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("Invalid signature"))
                    .build();
            }
            
            logger.info("Received Paystack webhook");
            
            // Process webhook
            walletService.processPaystackWebhook(payload);
            
            return Response.ok(ApiResponse.success(null, "Webhook processed")).build();
            
        } catch (Exception e) {
            logger.severe("Error processing Paystack webhook: " + e.getMessage());
            // Return 200 to prevent Paystack from retrying
            return Response.ok(ApiResponse.success(null, "Webhook received")).build();
        }
    }
    
    // ==================== BANK ACCOUNT MANAGEMENT ====================
    
    @GET
    @Path("/banks")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "List Nigerian banks", description = "Get list of Nigerian banks for withdrawal")
    public Response listBanks() {
        
        try {
            logger.info("Fetching Nigerian banks list");
            
            BankListResponse response = walletService.getNigerianBanks();
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error fetching banks list: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to fetch banks list"))
                .build();
        }
    }
    
    @POST
    @Path("/verify-account")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Verify bank account", description = "Verify bank account number and get account name")
    public Response verifyBankAccount(@Valid AccountVerificationRequest request) {
        
        try {
            logger.info("Verifying bank account: bank=" + request.getBankCode() + 
                ", account=" + request.getAccountNumber());
            
            AccountVerificationResponse response = walletService.verifyBankAccount(
                request.getAccountNumber(),
                request.getBankCode()
            );
            
            if (response.isValid()) {
                return Response.ok(ApiResponse.success(response, "Account verified successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid account number"))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error verifying bank account: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to verify account"))
                .build();
        }
    }
    
    // ==================== WALLET STATUS ====================
    
    @GET
    @Path("/status")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get wallet status", description = "Check if wallet is active and get limits")
    public Response getWalletStatus() {
        
        try {
            UUID userId = getUserId();
            
            WalletStatusResponse response = walletService.getWalletStatus(userId);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error getting wallet status: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get wallet status"))
                .build();
        }
    }
    
    // ==================== ADMIN ENDPOINTS ====================
    
    @PUT
    @Path("/{userId}/block")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Block wallet", description = "Admin endpoint to block a user's wallet")
    public Response blockWallet(
            @PathParam("userId") UUID userId,
            BlockWalletRequest request) {
        
        try {
            UUID adminId = getUserId();
            logger.info("Admin " + adminId + " blocking wallet for user " + userId);
            
            walletService.blockWallet(userId, request.getReason());
            
            return Response.ok(ApiResponse.success(null, "Wallet blocked successfully")).build();
            
        } catch (Exception e) {
            logger.severe("Error blocking wallet: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to block wallet"))
                .build();
        }
    }
    
    @PUT
    @Path("/{userId}/unblock")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Unblock wallet", description = "Admin endpoint to unblock a user's wallet")
    public Response unblockWallet(@PathParam("userId") UUID userId) {
        
        try {
            UUID adminId = getUserId();
            logger.info("Admin " + adminId + " unblocking wallet for user " + userId);
            
            walletService.unblockWallet(userId);
            
            return Response.ok(ApiResponse.success(null, "Wallet unblocked successfully")).build();
            
        } catch (Exception e) {
            logger.severe("Error unblocking wallet: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to unblock wallet"))
                .build();
        }
    }
    
    @POST
    @Path("/{userId}/adjust")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Adjust wallet balance", description = "Admin endpoint to manually adjust wallet balance")
    public Response adjustWalletBalance(
            @PathParam("userId") UUID userId,
            @Valid WalletAdjustmentRequest request) {
        
        try {
            UUID adminId = getUserId();
            logger.info("Admin " + adminId + " adjusting wallet balance for user " + userId + 
                ": amount=" + request.getAmount() + ", reason=" + request.getReason());
            
            walletService.adjustWalletBalance(
                userId,
                request.getAmount(),
                request.getReason(),
                adminId
            );
            
            return Response.ok(ApiResponse.success(null, "Wallet balance adjusted successfully")).build();
            
        } catch (Exception e) {
            logger.severe("Error adjusting wallet balance: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to adjust wallet balance"))
                .build();
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private UUID getUserId() {
        Principal principal = securityContext.getUserPrincipal();
        if (principal == null) {
            throw new WebApplicationException("Unauthorized", Response.Status.UNAUTHORIZED);
        }
        return UUID.fromString(principal.getName());
    }
    
    private String getUserEmail() {
        // TODO: Extract email from security context or user details
        // This is a placeholder - implement based on your security setup
        return "user@example.com";
    }
}