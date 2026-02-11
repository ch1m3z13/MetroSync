package com.commute.metrosync.resource;

import com.commute.metrosync.dto.*;
import com.commute.metrosync.service.AuthService;
import com.commute.metrosync.service.OtpService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Authentication Resource
 * Handles OTP-based authentication, registration, and login
 * Jakarta EE / JAX-RS implementation
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "OTP-based authentication endpoints")
@PermitAll
public class AuthResource {
    
    private static final Logger logger = Logger.getLogger(AuthResource.class.getName());
    
    @Inject
    private AuthService authService;
    
    @Inject
    private OtpService otpService;
    
    @Context
    private UriInfo uriInfo;
    
    // ==================== OTP ENDPOINTS ====================
    
    @POST
    @Path("/send-otp")
    @Operation(summary = "Send OTP", description = "Send OTP code to phone number via SMS")
    public Response sendOtp(
            @Valid SendOtpRequest request,
            @Context HttpServletRequest httpRequest) {
        
        try {
            String ipAddress = getClientIpAddress(httpRequest);
            logger.info("Sending OTP to " + maskPhoneNumber(request.getPhoneNumber()) + 
                ": purpose=" + request.getPurpose() + ", ip=" + ipAddress);
            
            // Validate phone number format (Nigerian)
            if (!request.getPhoneNumber().matches("^\\+234[7-9][0-1][0-9]{8}$")) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid Nigerian phone number format. Expected: +234XXXXXXXXXX"))
                    .build();
            }
            
            // Check if phone is already registered (for REGISTRATION purpose)
            if ("REGISTRATION".equals(request.getPurpose())) {
                if (authService.isPhoneNumberRegistered(request.getPhoneNumber())) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity(ApiResponse.error("Phone number already registered"))
                        .build();
                }
            }
            
            SendOtpResponse response = otpService.sendOtp(
                request.getPhoneNumber(),
                request.getPurpose(),
                ipAddress,
                httpRequest.getHeader("User-Agent")
            );
            
            if (response.isSuccess()) {
                return Response.ok(ApiResponse.success(response, "OTP sent successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getMessage()))
                    .build();
            }
            
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid OTP request: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        } catch (Exception e) {
            logger.severe("Error sending OTP: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to send OTP"))
                .build();
        }
    }
    
    @POST
    @Path("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verify OTP code entered by user")
    public Response verifyOtp(@Valid VerifyOtpRequest request) {
        
        try {
            logger.info("Verifying OTP for " + maskPhoneNumber(request.getPhoneNumber()) + 
                ": purpose=" + request.getPurpose());
            
            VerifyOtpResponse response = otpService.verifyOtp(
                request.getPhoneNumber(),
                request.getOtpCode(),
                request.getPurpose()
            );
            
            if (response.isSuccess()) {
                return Response.ok(ApiResponse.success(response, "OTP verified successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getMessage()))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error verifying OTP: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to verify OTP"))
                .build();
        }
    }
    
    @POST
    @Path("/resend-otp")
    @Operation(summary = "Resend OTP", description = "Resend OTP code to phone number")
    public Response resendOtp(
            @Valid ResendOtpRequest request,
            @Context HttpServletRequest httpRequest) {
        
        try {
            String ipAddress = getClientIpAddress(httpRequest);
            logger.info("Resending OTP to " + maskPhoneNumber(request.getPhoneNumber()) + 
                ": purpose=" + request.getPurpose());
            
            SendOtpResponse response = otpService.sendOtp(
                request.getPhoneNumber(),
                request.getPurpose(),
                ipAddress,
                httpRequest.getHeader("User-Agent")
            );
            
            if (response.isSuccess()) {
                return Response.ok(ApiResponse.success(response, "OTP resent successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getMessage()))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error resending OTP: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to resend OTP"))
                .build();
        }
    }
    
    // ==================== REGISTRATION ====================
    
    @POST
    @Path("/register")
    @Operation(summary = "Register with OTP", description = "Register new user with OTP-verified phone number")
    public Response register(@Valid RegisterRequest request) {
        
        try {
            logger.info("Registering new user: phone=" + maskPhoneNumber(request.getPhoneNumber()) + 
                ", userType=" + request.getUserType());
            
            // Verify OTP first
            VerifyOtpResponse otpVerification = otpService.verifyOtp(
                request.getPhoneNumber(),
                request.getOtpCode(),
                "REGISTRATION"
            );
            
            if (!otpVerification.isSuccess()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("OTP verification failed: " + otpVerification.getMessage()))
                    .build();
            }
            
            // Register user
            AuthResponse response = authService.registerWithOtp(request);
            
            return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success(response, "Registration successful"))
                .build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid registration request: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        } catch (Exception e) {
            logger.severe("Error during registration: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Registration failed"))
                .build();
        }
    }
    
    // ==================== LOGIN ====================
    
    @POST
    @Path("/login")
    @Operation(summary = "Login with password", description = "Login with email/phone and password")
    public Response login(@Valid LoginRequest request) {
        
        try {
            logger.info("User login attempt: identifier=" + request.getIdentifier());
            
            AuthResponse response = authService.login(request);
            
            return Response.ok(ApiResponse.success(response, "Login successful")).build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid login credentials: " + e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiResponse.error("Invalid credentials"))
                .build();
        } catch (Exception e) {
            logger.severe("Error during login: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Login failed"))
                .build();
        }
    }
    
    @POST
    @Path("/login-with-otp")
    @Operation(summary = "Login with OTP", description = "Passwordless login using OTP verification")
    public Response loginWithOtp(@Valid LoginWithOtpRequest request) {
        
        try {
            logger.info("OTP login attempt: phone=" + maskPhoneNumber(request.getPhoneNumber()));
            
            // Verify OTP
            VerifyOtpResponse otpVerification = otpService.verifyOtp(
                request.getPhoneNumber(),
                request.getOtpCode(),
                "LOGIN"
            );
            
            if (!otpVerification.isSuccess()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("OTP verification failed: " + otpVerification.getMessage()))
                    .build();
            }
            
            // Login user
            AuthResponse response = authService.loginWithOtp(request.getPhoneNumber());
            
            return Response.ok(ApiResponse.success(response, "Login successful")).build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("OTP login failed: " + e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        } catch (Exception e) {
            logger.severe("Error during OTP login: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Login failed"))
                .build();
        }
    }
    
    // ==================== PASSWORD RESET ====================
    
    @POST
    @Path("/forgot-password")
    @Operation(summary = "Forgot password", description = "Initiate password reset via OTP")
    public Response forgotPassword(
            @Valid ForgotPasswordRequest request,
            @Context HttpServletRequest httpRequest) {
        
        try {
            logger.info("Password reset request for: " + maskPhoneNumber(request.getPhoneNumber()));
            
            // Check if phone number exists
            if (!authService.isPhoneNumberRegistered(request.getPhoneNumber())) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Phone number not registered"))
                    .build();
            }
            
            String ipAddress = getClientIpAddress(httpRequest);
            
            SendOtpResponse response = otpService.sendOtp(
                request.getPhoneNumber(),
                "PASSWORD_RESET",
                ipAddress,
                httpRequest.getHeader("User-Agent")
            );
            
            if (response.isSuccess()) {
                return Response.ok(ApiResponse.success(response, "OTP sent for password reset")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getMessage()))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error during forgot password: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to process password reset request"))
                .build();
        }
    }
    
    @POST
    @Path("/reset-password")
    @Operation(summary = "Reset password", description = "Reset password with OTP verification")
    public Response resetPassword(@Valid ResetPasswordRequest request) {
        
        try {
            logger.info("Password reset for: " + maskPhoneNumber(request.getPhoneNumber()));
            
            // Verify OTP
            VerifyOtpResponse otpVerification = otpService.verifyOtp(
                request.getPhoneNumber(),
                request.getOtpCode(),
                "PASSWORD_RESET"
            );
            
            if (!otpVerification.isSuccess()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("OTP verification failed: " + otpVerification.getMessage()))
                    .build();
            }
            
            // Reset password
            authService.resetPassword(request.getPhoneNumber(), request.getNewPassword());
            
            return Response.ok(ApiResponse.success(null, "Password reset successful")).build();
            
        } catch (Exception e) {
            logger.severe("Error resetting password: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to reset password"))
                .build();
        }
    }
    
    // ==================== PHONE VERIFICATION ====================
    
    @POST
    @Path("/verify-phone")
    @Operation(summary = "Verify phone number", description = "Verify or change phone number with OTP")
    public Response verifyPhoneNumber(@Valid VerifyPhoneRequest request) {
        
        try {
            logger.info("Phone verification request: " + maskPhoneNumber(request.getPhoneNumber()));
            
            // Verify OTP
            VerifyOtpResponse otpVerification = otpService.verifyOtp(
                request.getPhoneNumber(),
                request.getOtpCode(),
                "PHONE_VERIFICATION"
            );
            
            if (!otpVerification.isSuccess()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("OTP verification failed: " + otpVerification.getMessage()))
                    .build();
            }
            
            // Update phone verification status
            authService.verifyPhoneNumber(request.getUserId(), request.getPhoneNumber());
            
            return Response.ok(ApiResponse.success(null, "Phone number verified successfully")).build();
            
        } catch (Exception e) {
            logger.severe("Error verifying phone number: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to verify phone number"))
                .build();
        }
    }
    
    // ==================== TOKEN REFRESH ====================
    
    @POST
    @Path("/refresh")
    @Operation(summary = "Refresh access token", description = "Get new access token using refresh token")
    public Response refreshToken(@Valid RefreshTokenRequest request) {
        
        try {
            logger.info("Token refresh request");
            
            RefreshTokenResponse response = authService.refreshToken(request.getRefreshToken());
            
            return Response.ok(ApiResponse.success(response, "Token refreshed successfully")).build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid refresh token: " + e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiResponse.error("Invalid or expired refresh token"))
                .build();
        } catch (Exception e) {
            logger.severe("Error refreshing token: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to refresh token"))
                .build();
        }
    }
    
    // ==================== LOGOUT ====================
    
    @POST
    @Path("/logout")
    @Operation(summary = "Logout", description = "Invalidate refresh token")
    public Response logout(@Valid LogoutRequest request) {
        
        try {
            logger.info("Logout request");
            
            authService.logout(request.getRefreshToken());
            
            return Response.ok(ApiResponse.success(null, "Logout successful")).build();
            
        } catch (Exception e) {
            logger.severe("Error during logout: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to logout"))
                .build();
        }
    }
    
    // ==================== ACCOUNT STATUS ====================
    
    @GET
    @Path("/check-phone/{phoneNumber}")
    @Operation(summary = "Check phone number", description = "Check if phone number is already registered")
    public Response checkPhoneNumber(@PathParam("phoneNumber") String phoneNumber) {
        
        try {
            logger.info("Checking phone number: " + maskPhoneNumber(phoneNumber));
            
            boolean registered = authService.isPhoneNumberRegistered(phoneNumber);
            
            PhoneCheckResponse response = new PhoneCheckResponse(phoneNumber, registered);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error checking phone number: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to check phone number"))
                .build();
        }
    }
    
    @GET
    @Path("/check-email/{email}")
    @Operation(summary = "Check email", description = "Check if email is already registered")
    public Response checkEmail(@PathParam("email") String email) {
        
        try {
            logger.info("Checking email: " + email);
            
            boolean registered = authService.isEmailRegistered(email);
            
            EmailCheckResponse response = new EmailCheckResponse(email, registered);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error checking email: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to check email"))
                .build();
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return "***";
        }
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}