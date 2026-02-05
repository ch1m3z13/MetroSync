package com.commute.metrosync.resource;


import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import com.commute.metrosync.dto.request.CreateRouteRequest;
import com.commute.metrosync.service.RouteService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Routes", description = "Route management for drivers")
@SecurityRequirement(name = "jwt")
public class RouteResource {

    @Inject
    RouteService routeService;

    @POST
    @RolesAllowed("DRIVER")
    @Operation(summary = "Create route", description = "Create a new daily route")
    public Response createRoute(@Valid CreateRouteRequest request, 
                               @Context SecurityContext ctx) {
        // Extract userId from JWT claim
        Long userId = Long.valueOf(ctx.getUserPrincipal().getName());
        return Response.ok(routeService.createRoute(userId, request)).build();
    }

    @GET
    @Path("/me")
    @RolesAllowed("DRIVER")
    @Operation(summary = "Get my routes", description = "Get all routes for current driver")
    public Response getMyRoutes(@Context SecurityContext ctx) {
        Long userId = Long.valueOf(ctx.getUserPrincipal().getName());
        return Response.ok(routeService.getDriverRoutes(userId)).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get route details", description = "Get route by ID")
    public Response getRoute(@PathParam("id") Long id) {
        return Response.ok(routeService.getRoute(id)).build();
    }
}