package com.commute.metrosync.resource;

import com.commute.metrosync.dto.LoginDTO;
import com.commute.metrosync.dto.UserDTO;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.UserRepository;
import com.commute.metrosync.service.PasswordService;
import com.commute.metrosync.service.TokenService;
import com.commute.metrosync.dto.ErrorResponse;

import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/auth")
@Produces("application/json")
@Consumes("application/json")
public class AuthResource {

    @Inject
    UserRepository userRepository;
    
    @Inject
    PasswordService passwordService;
    
    @Inject
    TokenService tokenService;

    @Inject
    JsonWebToken jwt; // Used for validating the Refresh Token

    /**
     * Login: Exchange Username/Password for Access & Refresh Tokens
     */
    @POST
    @Path("/login")
    @PermitAll
    public Response login(LoginDTO loginDTO) {
        try {
            Log.info("Login attempt for: " + loginDTO.getUsername());

            // 1. Find user
            User user = userRepository.findByUsername(loginDTO.getUsername())
                .orElseThrow(() -> {
                    Log.warn("Login failed: Username not found - " + loginDTO.getUsername());
                    return new WebApplicationException(
                        Response.status(401)
                            .entity(new ErrorResponse("Invalid username or password"))
                            .build()
                    );
                });
            
            // 2. Verify password
            if (!passwordService.verifyPassword(loginDTO.getPassword(), user.getPasswordHash())) {
                Log.warn("Login failed: Invalid password for user: " + loginDTO.getUsername());
                return Response.status(401)
                        .entity(new ErrorResponse("Invalid username or password"))
                        .build();
            }
            
            // 3. Generate Tokens
            String accessToken = tokenService.generateAccessToken(user);
            String refreshToken = tokenService.generateRefreshToken(user);
            
            Log.info("Login successful for user: " + user.getUsername());
            Log.info("Assigned Role from DB: " + user.getRole());

            // 4. Create Response
            UserDTO userDTO = new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole()
            );
            
            // Use the unified TokenResponse to send back both tokens
            TokenResponse response = new TokenResponse(userDTO, accessToken, refreshToken);
            
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
     * Refresh: Exchange a valid Refresh Token for a new pair of tokens
     */
    @POST
    @Path("/refresh")
    @PermitAll
    public Response refreshToken() {
        try {
            // 1. Security Check: Ensure the incoming token is actually a REFRESH token
            // (We don't want people extending sessions using short-lived access tokens)
            String tokenType = jwt.getClaim("type");
            
            if (tokenType == null || !tokenType.equals("refresh")) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(new ErrorResponse("Invalid token type. Refresh token required."))
                        .build();
            }

            // 2. Identify the User from the token
            String userIdStr = jwt.getSubject();
            if (userIdStr == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(new ErrorResponse("Invalid token: no subject"))
                        .build();
            }

            UUID userId = UUID.fromString(userIdStr);
            User user = userRepository.findByIdOptional(userId)
                    .orElseThrow(() -> new WebApplicationException(
                        Response.status(Response.Status.UNAUTHORIZED)
                            .entity(new ErrorResponse("User not found"))
                            .build()
                    ));

            // 3. Token Rotation: Generate a fresh pair of tokens
            String newAccessToken = tokenService.generateAccessToken(user);
            String newRefreshToken = tokenService.generateRefreshToken(user);

            // 4. Create Response
            UserDTO userDTO = new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole()
            );

            TokenResponse response = new TokenResponse(
                userDTO,
                newAccessToken,
                newRefreshToken
            );

            return Response.ok(response).build();

        } catch (Exception e) {
            Log.error("Token Refresh Failed", e);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Token refresh failed"))
                    .build();
        }
    }

    // --- DTO Record ---
    // Using a Java Record for a clean, immutable response object
    // This matches what your Flutter ApiClient expects: { "token": "...", "refreshToken": "...", "user": ... }
    public record TokenResponse(
        UserDTO user,
        String token,        // Maps to 'token' in JSON (Access Token)
        String refreshToken  // Maps to 'refreshToken' in JSON
    ) {}
}