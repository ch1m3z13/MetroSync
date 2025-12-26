package com.commute.metrosync.resource;

import org.eclipse.microprofile.jwt.JsonWebToken;
import com.commute.metrosync.dto.RegisterDTO;
import com.commute.metrosync.dto.UserDTO;
import com.commute.metrosync.dto.LoginResponseDTO;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.UserRepository;
import com.commute.metrosync.service.PasswordService;
import com.commute.metrosync.service.TokenService;
import com.commute.metrosync.dto.ErrorResponse;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.UUID;

@Path("/users")
@Produces("application/json")
@Consumes("application/json")
public class UserResource {

    @Inject
    UserRepository userRepository;
    
    @Inject
    PasswordService passwordService;
    
    @Inject
    TokenService tokenService;
    
    @Inject
    JsonWebToken jwt;

    /**
     * Register new user - matches Flutter's API call
     */
    @POST
    @Path("/register")
    @PermitAll
    @Transactional
    public Response register(@Valid RegisterDTO registerDTO) {
        try {
            // Check if username exists
            if (userRepository.existsByUsername(registerDTO.getUsername())) {
                throw new WebApplicationException(
                    Response.status(409)
                        .entity(new ErrorResponse("Username already exists"))
                        .build()
                );
            }
            
            // Check if email exists
            if (userRepository.findByEmail(registerDTO.getEmail()).isPresent()) {
                throw new WebApplicationException(
                    Response.status(409)
                        .entity(new ErrorResponse("Email already exists"))
                        .build()
                );
            }
            
            // Create new user
            User user = new User();
            user.setUsername(registerDTO.getUsername());
            user.setPasswordHash(passwordService.hashPassword(registerDTO.getPassword()));
            user.setFullName(registerDTO.getFullName());
            user.setEmail(registerDTO.getEmail());
            user.setPhoneNumber(registerDTO.getPhoneNumber());
            
            // Set role based on isDriver flag
            String role = (registerDTO.getIsDriver() != null && registerDTO.getIsDriver()) 
                ? "DRIVER" 
                : "RIDER";
            user.setRole(role);
            
            userRepository.persist(user);
            
            // Generate token
            String token = tokenService.generateToken(user);
            
            // Create response
            UserDTO userDTO = new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole()
            );
            
            LoginResponseDTO response = new LoginResponseDTO(userDTO, token);
            
            return Response.status(201).entity(response).build();
            
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new WebApplicationException(
                Response.status(500)
                    .entity(new ErrorResponse("An error occurred during registration"))
                    .build()
            );
        }
    }

    /**
     * Get current authenticated user - /users/me endpoint
     */
    @GET
    @Path("/me")
    @RolesAllowed({"DRIVER", "RIDER", "ADMIN"})
    public Response getCurrentUser(@Context SecurityContext securityContext) {
        try {
            // Get user ID from JWT token
            String userId = jwt.getSubject();
            
            // Find user
            User user = userRepository.findById(UUID.fromString(userId));
            if (user == null) {
                throw new WebApplicationException(
                    Response.status(404)
                        .entity(new ErrorResponse("User not found"))
                        .build()
                );
            }
            
            // Create user DTO
            UserDTO userDTO = new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole()
            );
            
            return Response.ok(userDTO).build();
            
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new WebApplicationException(
                Response.status(500)
                    .entity(new ErrorResponse("An error occurred"))
                    .build()
            );
        }
    }
}