package com.commute.metrosync.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import com.commute.metrosync.entity.*;
import com.commute.metrosync.repository.*;
import com.commute.metrosync.service.BookingService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Users", description = "User management and authentication")
public class UserResource {
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    VehicleRepository vehicleRepository;
    
    /**
     * Register a new user.
     */
    @POST
    @Path("/register")
    @PermitAll
    @Transactional
    @Operation(summary = "Register a new user")
    public Response register(@Valid RegisterUserDTO request) {
        // Check if username/email already exists
        if (userRepository.existsByUsername(request.username)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("Username already exists"))
                    .build();
        }
        
        if (userRepository.existsByEmail(request.email)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("Email already exists"))
                    .build();
        }
        
        // Create user
        User user = new User(
            request.username,
            request.password,
            request.fullName,
            request.email,
            request.phoneNumber
        );
        
        // Set roles
        if (request.isDriver != null && request.isDriver) {
            user.addRole(UserRole.DRIVER);
        }
        
        User savedUser = userRepository.save(user);
        
        return Response.status(Response.Status.CREATED)
                .entity(toUserDTO(savedUser))
                .build();
    }
    
    /**
     * Get user profile.
     */
    @GET
    @Path("/{userId}")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get user profile")
    public Response getUser(@PathParam("userId") UUID userId) {
        return userRepository.findById(userId)
                .map(user -> Response.ok(toUserDTO(user)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
    
    /**
     * Update user profile.
     */
    @PUT
    @Path("/{userId}")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Transactional
    @Operation(summary = "Update user profile")
    public Response updateUser(
            @PathParam("userId") UUID userId,
            @Valid UpdateUserDTO request) {
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        if (request.fullName != null) user.setFullName(request.fullName);
        if (request.phoneNumber != null) user.setPhoneNumber(request.phoneNumber);
        if (request.profileImageUrl != null) user.setProfileImageUrl(request.profileImageUrl);
        
        User updated = userRepository.save(user);
        return Response.ok(toUserDTO(updated)).build();
    }
    
    /**
     * Add a vehicle (Driver only).
     */
    @POST
    @Path("/{userId}/vehicles")
    @RolesAllowed({"DRIVER"})
    @Transactional
    @Operation(summary = "Add a vehicle", description = "Register a new vehicle for a driver")
    public Response addVehicle(
            @PathParam("userId") UUID userId,
            @Valid AddVehicleDTO request) {
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        if (!user.isDriver()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("User is not a driver"))
                    .build();
        }
        
        Vehicle vehicle = new Vehicle(
            user,
            request.make,
            request.model,
            request.year,
            request.color,
            request.licensePlate,
            request.capacity
        );
        
        if (request.vehicleType != null) {
            vehicle.setVehicleType(VehicleType.valueOf(request.vehicleType));
        }
        
        Vehicle saved = vehicleRepository.save(vehicle);
        
        return Response.status(Response.Status.CREATED)
                .entity(toVehicleDTO(saved))
                .build();
    }
    
    /**
     * Get user's vehicles.
     */
    @GET
    @Path("/{userId}/vehicles")
    @RolesAllowed({"DRIVER"})
    @Operation(summary = "Get user's vehicles")
    public Response getUserVehicles(@PathParam("userId") UUID userId) {
        List<Vehicle> vehicles = vehicleRepository.findByOwner(userId);
        return Response.ok(vehicles.stream().map(this::toVehicleDTO).toList()).build();
    }
    
    // Helper methods
    private UserDTO toUserDTO(User user) {
        return new UserDTO(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            user.getPhoneNumber(),
            user.getRoles(),
            user.getRating(),
            user.getTotalRatings(),
            user.getIsVerified()
        );
    }
    
    private VehicleDTO toVehicleDTO(Vehicle vehicle) {
        return new VehicleDTO(
            vehicle.getId(),
            vehicle.getMake(),
            vehicle.getModel(),
            vehicle.getYear(),
            vehicle.getColor(),
            vehicle.getLicensePlate(),
            vehicle.getCapacity(),
            vehicle.getVehicleType().name(),
            vehicle.getIsVerified()
        );
    }
    
    // DTOs
    public record RegisterUserDTO(
        @NotNull @Size(min = 3, max = 50) String username,
        @NotNull @Size(min = 6, max = 100) String password,
        @NotNull @Size(min = 2, max = 100) String fullName,
        @NotNull @Email String email,
        @NotNull @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$") String phoneNumber,
        Boolean isDriver
    ) {}
    
    public record UpdateUserDTO(
        @Size(min = 2, max = 100) String fullName,
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$") String phoneNumber,
        @Size(max = 500) String profileImageUrl
    ) {}
    
    public record AddVehicleDTO(
        @NotNull @Size(min = 2, max = 50) String make,
        @NotNull @Size(min = 2, max = 50) String model,
        @NotNull @Min(1900) @Max(2100) Integer year,
        @NotNull @Size(min = 2, max = 30) String color,
        @NotNull @Size(min = 3, max = 20) String licensePlate,
        @NotNull @Min(1) @Max(50) Integer capacity,
        String vehicleType
    ) {}
    
    public record UserDTO(
        UUID id,
        String username,
        String fullName,
        String email,
        String phoneNumber,
        String roles,
        java.math.BigDecimal rating,
        Integer totalRatings,
        Boolean isVerified
    ) {}
    
    public record VehicleDTO(
        UUID id,
        String make,
        String model,
        Integer year,
        String color,
        String licensePlate,
        Integer capacity,
        String vehicleType,
        Boolean isVerified
    ) {}
    
    public record ErrorResponse(String message) {}
}
