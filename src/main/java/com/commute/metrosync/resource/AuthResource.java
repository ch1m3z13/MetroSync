package com.commute.metrosync.resource;

import com.commute.metrosync.dto.LoginDTO;
import com.commute.metrosync.dto.LoginResponseDTO;
import com.commute.metrosync.dto.UserDTO;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.UserRepository;
import com.commute.metrosync.service.PasswordService;
import com.commute.metrosync.service.TokenService;
import com.commute.metrosync.dto.ErrorResponse;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

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

    /**
     * Login endpoint - matches Flutter's expected response
     */
    @POST
    @Path("/login")
    @PermitAll
    public Response login(LoginDTO loginDTO) {
        try {
            // Find user by username
            User user = userRepository.findByUsername(loginDTO.getUsername())
                .orElseThrow(() -> new WebApplicationException(
                    Response.status(401)
                        .entity(new ErrorResponse("Invalid username or password"))
                        .build()
                ));
            
            // Verify password
            if (!passwordService.verifyPassword(loginDTO.getPassword(), user.getPasswordHash())) {
                throw new WebApplicationException(
                    Response.status(401)
                        .entity(new ErrorResponse("Invalid username or password"))
                        .build()
                );
            }
            
            // Generate token
            String token = tokenService.generateToken(user);
            
            // Create user DTO
            UserDTO userDTO = new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole()
            );
            
            // Return response in Flutter's expected format: { "user": {...}, "token": "..." }
            LoginResponseDTO response = new LoginResponseDTO(userDTO, token);
            
            return Response.ok(response).build();
            
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new WebApplicationException(
                Response.status(500)
                    .entity(new ErrorResponse("An error occurred during login"))
                    .build()
            );
        }
    }
}