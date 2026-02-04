package com.commute.metrosync.resource;

import com.commute.metrosync.dto.ErrorResponse;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.UserRepository;
import com.commute.metrosync.service.PasswordService;
import com.commute.metrosync.service.TokenService;

import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Authentication & Authorization API
 * 
 * Endpoints:
 * - POST /auth/register: Create new user account
 * - POST /auth/login: Authenticate and get JWT token
 * - GET /auth/me: Get current authenticated user details
 * - POST /auth/refresh: Refresh access token (optional)
 */
@Path("/auth")
@Produces("application/json")
@Consumes("application/json")
@Tag(name = "Authentication", description = "User authentication and authorization")
public class AuthResource {

    @Inject
    UserRepository userRepository;
    
    @Inject
    PasswordService passwordService;
    
    @Inject
    TokenService tokenService;

    @Inject
    JsonWebToken jwt;

    /**
     * POST /auth/register
     * Create new user account
     * 
     * Validation:
     * - Email must be valid format and unique
     * - Password minimum 8 characters
     * - Roles array must contain at least PASSENGER or DRIVER
     */
    @POST
    @Path("/register")
    @PermitAll
    @Transactional
    @Operation(
        summary = "Register new user",
        description = "Create a new user account with email and password. Roles can be PASSENGER, DRIVER, or both."
    )
    public Response register(@Valid RegisterRequest request) {
        try {
            Log.info("Registration attempt for email: " + request.email());

            // 1. Validate email is unique
            if (userRepository.findByEmail(request.email()).isPresent()) {
                return Response.status(409)
                        .entity(new ErrorResponse("Email already exists"))
                        .build();
            }
            
            // 2. Validate password strength (minimum 8 characters from annotation)
            // Additional validation happens via @Valid annotation
            
            // 3. Validate roles array
            if (request.roles() == null || request.roles().isEmpty()) {
                return Response.status(400)
                        .entity(new ErrorResponse("At least one role (PASSENGER or DRIVER) is required"))
                        .build();
            }
            
            // Validate roles are valid
            List<String> validRoles = Arrays.asList("PASSENGER", "DRIVER");
            for (String role : request.roles()) {
                if (!validRoles.contains(role)) {
                    return Response.status(400)
                            .entity(new ErrorResponse("Invalid role: " + role + ". Must be PASSENGER or DRIVER."))
                            .build();
                }
            }
            
            // 4. Create new user
            User user = new User();
            user.setUsername(extractUsernameFromEmail(request.email())); // Generate username from email
            user.setPasswordHash(passwordService.hashPassword(request.password()));
            user.setFullName(request.fullName());
            user.setEmail(request.email());
            user.setPhoneNumber(request.phoneNumber()); // Optional, can be null
            
            // Set roles as comma-separated string for database storage
            user.setRoles(String.join(",", request.roles()));
            user.setIsActive(true);
            user.setIsVerified(false);
            
            Log.info("Assigned roles: " + user.getRoles());
            
            // 5. Persist user
            userRepository.persist(user);
            userRepository.flush();
            
            Log.info("User registered successfully with ID: " + user.getId());
            
            // 6. Return success response (201 Created)
            return Response.status(201)
                    .entity(new RegisterResponse(
                        "User registered successfully",
                        user.getId().toString()
                    ))
                    .build();
            
        } catch (Exception e) {
            Log.error("Registration failed", e);
            return Response.status(500)
                    .entity(new ErrorResponse("An error occurred during registration: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * POST /auth/login
     * Authenticate user and get JWT token
     * 
     * Response includes:
     * - Access token (JWT) - valid for 24 hours (configurable)
     * - User details (id, email, fullName, roles)
     */
    @POST
    @Path("/login")
    @PermitAll
    @Operation(
        summary = "User login",
        description = "Authenticate with email and password to receive JWT token"
    )
    public Response login(@Valid LoginRequest request) {
        try {
            Log.info("Login attempt for email: " + request.email());

            // 1. Find user by email
            User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    Log.warn("Login failed: Email not found - " + request.email());
                    return new WebApplicationException(
                        Response.status(401)
                            .entity(new ErrorResponse("Invalid credentials"))
                            .build()
                    );
                });
            
            // 2. Verify password
            if (!passwordService.verifyPassword(request.password(), user.getPasswordHash())) {
                Log.warn("Login failed: Invalid password for email: " + request.email());
                return Response.status(401)
                        .entity(new ErrorResponse("Invalid credentials"))
                        .build();
            }
            
            // 3. Update last login timestamp
            user.setLastLogin(LocalDateTime.now());
            userRepository.persist(user);
            
            // 4. Generate JWT access token
            String token = tokenService.generateAccessToken(user);
            
            Log.info("Login successful for user: " + user.getEmail());

            // 5. Create response with user details and token
            UserResponse userResponse = new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                parseRoles(user.getRoles())
            );
            
            LoginResponse response = new LoginResponse(token, userResponse);
            
            return Response.ok(response).build();
            
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            Log.error("Login Error", e);
            return Response.status(500)
                    .entity(new ErrorResponse("An error occurred during login"))
                    .build();
        }
    }

    /**
     * GET /auth/me
     * Get current authenticated user details
     * 
     * Requires: Authorization header with valid JWT token
     * Returns: User profile information
     */
    @GET
    @Path("/me")
    @RolesAllowed({"PASSENGER", "DRIVER"})
    @Operation(
        summary = "Get current user",
        description = "Get details of the currently authenticated user"
    )
    public Response getCurrentUser() {
        try {
            // Get user ID from JWT token subject
            String userId = jwt.getSubject();
            
            if (userId == null) {
                return Response.status(401)
                        .entity(new ErrorResponse("Invalid token"))
                        .build();
            }
            
            // Find user by ID
            User user = userRepository.findByIdOptional(UUID.fromString(userId))
                    .orElseThrow(() -> new WebApplicationException(
                        Response.status(404)
                            .entity(new ErrorResponse("User not found"))
                            .build()
                    ));
            
            // Create response with user details
            CurrentUserResponse response = new CurrentUserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                parseRoles(user.getRoles()),
                user.getCreatedAt()
            );
            
            return Response.ok(response).build();
            
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            Log.error("Get Me failed", e);
            return Response.status(500)
                    .entity(new ErrorResponse("An error occurred"))
                    .build();
        }
    }

    /**
     * POST /auth/refresh
     * Refresh access token using refresh token
     * (Optional - for future enhancement)
     * 
     * This endpoint allows clients to get a new access token
     * without requiring the user to log in again.
     */
    @POST
    @Path("/refresh")
    @PermitAll
    @Operation(
        summary = "Refresh token",
        description = "Get new access token using refresh token"
    )
    public Response refreshToken() {
        try {
            // Verify the incoming token is a refresh token
            String tokenType = jwt.getClaim("type");
            
            if (tokenType == null || !tokenType.equals("refresh")) {
                return Response.status(401)
                        .entity(new ErrorResponse("Invalid token type. Refresh token required."))
                        .build();
            }

            // Get user from token
            String userIdStr = jwt.getSubject();
            if (userIdStr == null) {
                return Response.status(401)
                        .entity(new ErrorResponse("Invalid token: no subject"))
                        .build();
            }

            UUID userId = UUID.fromString(userIdStr);
            User user = userRepository.findByIdOptional(userId)
                    .orElseThrow(() -> new WebApplicationException(
                        Response.status(401)
                            .entity(new ErrorResponse("User not found"))
                            .build()
                    ));

            // Generate fresh tokens
            String newAccessToken = tokenService.generateAccessToken(user);
            String newRefreshToken = tokenService.generateRefreshToken(user);

            // Create user DTO
            UserResponse userResponse = new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                parseRoles(user.getRoles())
            );

            RefreshResponse response = new RefreshResponse(
                newAccessToken,
                newRefreshToken,
                userResponse
            );

            return Response.ok(response).build();

        } catch (Exception e) {
            Log.error("Token Refresh Failed", e);
            return Response.status(401)
                    .entity(new ErrorResponse("Token refresh failed"))
                    .build();
        }
    }

    // ==================== HELPER METHODS ====================
    
    /**
     * Extract username from email (part before @)
     */
    private String extractUsernameFromEmail(String email) {
        return email.substring(0, email.indexOf('@'));
    }
    
    /**
     * Parse comma-separated roles string into List
     */
    private List<String> parseRoles(String rolesString) {
        if (rolesString == null || rolesString.isEmpty()) {
            return List.of("PASSENGER");
        }
        return Arrays.asList(rolesString.split(","));
    }

    // ==================== REQUEST DTOs ====================
    
    /**
     * POST /auth/register request body
     */
    public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,
        
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,
        
        @NotBlank(message = "Full name is required")
        String fullName,
        
        String phoneNumber,  // Optional
        
        @NotNull(message = "Roles are required")
        @Size(min = 1, message = "At least one role is required")
        List<String> roles   // ["PASSENGER"] or ["DRIVER"] or ["PASSENGER", "DRIVER"]
    ) {}
    
    /**
     * POST /auth/login request body
     */
    public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,
        
        @NotBlank(message = "Password is required")
        String password
    ) {}

    // ==================== RESPONSE DTOs ====================
    
    /**
     * POST /auth/register response
     */
    public record RegisterResponse(
        String message,
        String userId
    ) {}
    
    /**
     * POST /auth/login response
     */
    public record LoginResponse(
        String token,
        UserResponse user
    ) {}
    
    /**
     * User details in login/register responses
     */
    public record UserResponse(
        String id,
        String email,
        String fullName,
        String phoneNumber,
        List<String> roles
    ) {}
    
    /**
     * GET /auth/me response (includes createdAt)
     */
    public record CurrentUserResponse(
        String id,
        String email,
        String fullName,
        String phoneNumber,
        List<String> roles,
        LocalDateTime createdAt
    ) {}
    
    /**
     * POST /auth/refresh response
     */
    public record RefreshResponse(
        String token,
        String refreshToken,
        UserResponse user
    ) {}
}