package com.commute.metrosync.resource;

package com.commute.metrosync.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.commute.metrosync.dto.request.LoginRequest;
import com.commute.metrosync.dto.request.RegisterRequest;
import com.commute.metrosync.dto.request.VerifyOtpRequest;
import com.commute.metrosync.service.AuthService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "User authentication endpoints")
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/register")
    @Operation(summary = "Register new user", description = "Register with phone number and receive OTP")
    public Response register(@Valid RegisterRequest request) {
        return Response.ok(authService.register(request)).build();
    }

    @POST
    @Path("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verify OTP code and complete registration")
    public Response verifyOtp(@Valid VerifyOtpRequest request) {
        return Response.ok(authService.verifyOtp(request)).build();
    }

    @POST
    @Path("/login")
    @Operation(summary = "Login", description = "Login with phone number and password or OTP")
    public Response login(@Valid LoginRequest request) {
        return Response.ok(authService.login(request)).build();
    }
}