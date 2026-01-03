package com.commute.metrosync.resource;

import com.commute.metrosync.dto.LoginResponseDTO;
import com.commute.metrosync.dto.UserDTO;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.UserRepository;
import com.commute.metrosync.service.TokenRefreshService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/auth")
@Produces("application/json")
@Consumes("application/json")
public class TokenRefreshResource {

    @Inject
    TokenRefreshService tokenRefreshService;
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    JsonWebToken jwt;

    /**
     * Refresh access token using refresh token
     * POST /auth/refresh
     * Authorization: Bearer <refresh_token>
     */
    @POST
    @Path("/refresh")
    @PermitAll
    public Response refreshToken() {
        try {
            // Validate it's a refresh token
            String tokenType = jwt.getClaim("type");
            if (!"refresh".equals(tokenType)) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(new ErrorResponse("Invalid token type"))
                        .build();
            }
            
            // Get user
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findByIdOptional(userId)
                    .orElseThrow(() -> new WebApplicationException(
                        Response.status(Response.Status.UNAUTHORIZED)
                            .entity(new ErrorResponse("User not found"))
                            .build()
                    ));
            
            // Generate new tokens
            TokenRefreshService.TokenPair tokens = tokenRefreshService.refreshAccessToken(jwt);
            
            // Create response
            UserDTO userDTO = new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole()
            );
            
            RefreshResponse response = new RefreshResponse(
                userDTO,
                tokens.accessToken(),
                tokens.refreshToken()
            );
            
            return Response.ok(response).build();
            
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Token refresh failed"))
                    .build();
        }
    }
    
    /**
     * Updated login response with both access and refresh tokens
     */
    public record RefreshResponse(
        UserDTO user,
        String accessToken,
        String refreshToken
    ) {}
    
    public record ErrorResponse(String message) {}
}