package com.commute.metrosync.resource;

import com.commute.metrosync.dto.*;
import com.commute.metrosync.service.FileStorageService;
import com.commute.metrosync.service.VerificationService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;

import java.security.Principal;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Verification Resource
 * Handles identity verification, employment verification, and driver document verification
 * Jakarta EE / JAX-RS implementation
 */
@Path("/api/v1/verification")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Verification", description = "User identity and driver verification endpoints")
@SecurityRequirement(name = "bearerAuth")
public class VerificationResource {
    
    private static final Logger logger = Logger.getLogger(VerificationResource.class.getName());
    
    @Inject
    private VerificationService verificationService;
    
    @Inject
    private FileStorageService fileStorageService;
    
    @Context
    private SecurityContext securityContext;
    
    @Context
    private UriInfo uriInfo;
    
    // ==================== FILE UPLOAD ENDPOINTS ====================
    
    @POST
    @Path("/upload/nin")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Upload NIN document", description = "Upload NIN card or slip for identity verification")
    public Response uploadNinDocument(@MultipartForm FileUploadForm form) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " uploading NIN document");
            
            FileStorageService.FileUploadResponse response = fileStorageService.uploadNinDocument(
                form.getFile(), 
                userId
            );
            
            if (response.isSuccess()) {
                FileUploadResponse dto = new FileUploadResponse(response);
                return Response.ok(ApiResponse.success(dto, "NIN document uploaded successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getErrorMessage()))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error uploading NIN document: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to upload NIN document"))
                .build();
        }
    }
    
    @POST
    @Path("/upload/selfie")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Upload selfie photo", description = "Upload selfie for identity verification")
    public Response uploadSelfie(@MultipartForm FileUploadForm form) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " uploading selfie");
            
            FileStorageService.FileUploadResponse response = fileStorageService.uploadSelfie(
                form.getFile(), 
                userId
            );
            
            if (response.isSuccess()) {
                FileUploadResponse dto = new FileUploadResponse(response);
                return Response.ok(ApiResponse.success(dto, "Selfie uploaded successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getErrorMessage()))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error uploading selfie: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to upload selfie"))
                .build();
        }
    }
    
    @POST
    @Path("/upload/driver-license")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("DRIVER")
    @Operation(summary = "Upload driver's license", description = "Upload driver's license for driver verification")
    public Response uploadDriverLicense(@MultipartForm FileUploadForm form) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " uploading driver's license");
            
            FileStorageService.FileUploadResponse response = fileStorageService.uploadDriverLicense(
                form.getFile(), 
                userId
            );
            
            if (response.isSuccess()) {
                FileUploadResponse dto = new FileUploadResponse(response);
                return Response.ok(ApiResponse.success(dto, "Driver's license uploaded successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getErrorMessage()))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error uploading driver's license: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to upload driver's license"))
                .build();
        }
    }
    
    @POST
    @Path("/upload/vehicle-registration")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("DRIVER")
    @Operation(summary = "Upload vehicle registration", description = "Upload vehicle registration document")
    public Response uploadVehicleRegistration(@MultipartForm FileUploadForm form) {
        
        try {
            UUID userId = getUserId();
            
            FileStorageService.FileUploadResponse response = fileStorageService.uploadVehicleRegistration(
                form.getFile(), 
                userId
            );
            
            if (response.isSuccess()) {
                FileUploadResponse dto = new FileUploadResponse(response);
                return Response.ok(ApiResponse.success(dto, "Vehicle registration uploaded successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getErrorMessage()))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error uploading vehicle registration: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to upload vehicle registration"))
                .build();
        }
    }
    
    @POST
    @Path("/upload/vehicle-insurance")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("DRIVER")
    @Operation(summary = "Upload vehicle insurance", description = "Upload vehicle insurance certificate")
    public Response uploadVehicleInsurance(@MultipartForm FileUploadForm form) {
        
        try {
            UUID userId = getUserId();
            
            FileStorageService.FileUploadResponse response = fileStorageService.uploadVehicleInsurance(
                form.getFile(), 
                userId
            );
            
            if (response.isSuccess()) {
                FileUploadResponse dto = new FileUploadResponse(response);
                return Response.ok(ApiResponse.success(dto, "Vehicle insurance uploaded successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getErrorMessage()))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error uploading vehicle insurance: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to upload vehicle insurance"))
                .build();
        }
    }
    
    @POST
    @Path("/upload/roadworthiness")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("DRIVER")
    @Operation(summary = "Upload roadworthiness certificate", description = "Upload VIO roadworthiness certificate")
    public Response uploadRoadworthiness(@MultipartForm FileUploadForm form) {
        
        try {
            UUID userId = getUserId();
            
            FileStorageService.FileUploadResponse response = fileStorageService.uploadRoadworthiness(
                form.getFile(), 
                userId
            );
            
            if (response.isSuccess()) {
                FileUploadResponse dto = new FileUploadResponse(response);
                return Response.ok(ApiResponse.success(dto, "Roadworthiness certificate uploaded successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getErrorMessage()))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error uploading roadworthiness certificate: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to upload roadworthiness certificate"))
                .build();
        }
    }
    
    @POST
    @Path("/upload/vehicle-photo")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("DRIVER")
    @Operation(summary = "Upload vehicle photo", description = "Upload vehicle photo (front, back, side, or interior)")
    public Response uploadVehiclePhoto(
            @MultipartForm FileUploadForm form,
            @QueryParam("photoType") String photoType) {
        
        try {
            UUID userId = getUserId();
            
            // Validate photo type
            if (photoType == null || !photoType.matches("^(front|back|side|interior)$")) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid photo type. Must be: front, back, side, or interior"))
                    .build();
            }
            
            FileStorageService.FileUploadResponse response = fileStorageService.uploadVehiclePhoto(
                form.getFile(), 
                userId, 
                photoType
            );
            
            if (response.isSuccess()) {
                FileUploadResponse dto = new FileUploadResponse(response);
                return Response.ok(ApiResponse.success(dto, "Vehicle photo uploaded successfully")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(response.getErrorMessage()))
                    .build();
            }
            
        } catch (Exception e) {
            logger.severe("Error uploading vehicle photo: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to upload vehicle photo"))
                .build();
        }
    }
    
    // ==================== IDENTITY VERIFICATION ====================
    
    @POST
    @Path("/identity")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Submit identity verification", description = "Submit NIN and personal information for verification")
    public Response submitIdentityVerification(@Valid IdentityVerificationRequest request) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " submitting identity verification");
            
            IdentityVerificationResponse response = verificationService.submitIdentityVerification(userId, request);
            
            return Response.ok(ApiResponse.success(response, "Identity verification submitted successfully")).build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid identity verification request: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        } catch (Exception e) {
            logger.severe("Error submitting identity verification: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to submit identity verification"))
                .build();
        }
    }
    
    // ==================== EMPLOYMENT VERIFICATION ====================
    
    @POST
    @Path("/employment")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Submit employment information", description = "Submit employment details for verification")
    public Response submitEmploymentVerification(@Valid EmploymentVerificationRequest request) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " submitting employment verification");
            
            EmploymentVerificationResponse response = verificationService.submitEmploymentVerification(userId, request);
            
            return Response.ok(ApiResponse.success(response, "Employment information submitted successfully")).build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid employment verification request: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        } catch (Exception e) {
            logger.severe("Error submitting employment verification: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to submit employment verification"))
                .build();
        }
    }
    
    // ==================== DRIVER DOCUMENTS VERIFICATION ====================
    
    @POST
    @Path("/driver-documents")
    @RolesAllowed("DRIVER")
    @Operation(summary = "Submit driver documents", description = "Submit driver's license and vehicle documents for verification")
    public Response submitDriverDocuments(@Valid DriverDocumentsRequest request) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " submitting driver documents");
            
            DriverDocumentsResponse response = verificationService.submitDriverDocuments(userId, request);
            
            return Response.ok(ApiResponse.success(response, "Driver documents submitted for review")).build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid driver documents request: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        } catch (Exception e) {
            logger.severe("Error submitting driver documents: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to submit driver documents"))
                .build();
        }
    }
    
    // ==================== VERIFICATION STATUS ====================
    
    @GET
    @Path("/status")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get verification status", description = "Get complete verification status for the current user")
    public Response getVerificationStatus() {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " requesting verification status");
            
            VerificationStatusResponse response = verificationService.getVerificationStatus(userId);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error getting verification status: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get verification status"))
                .build();
        }
    }
    
    @GET
    @Path("/status/{userId}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Get verification status for user", description = "Admin endpoint to get verification status for any user")
    public Response getVerificationStatusForUser(@PathParam("userId") UUID userId) {
        
        try {
            logger.info("Admin requesting verification status for user " + userId);
            
            VerificationStatusResponse response = verificationService.getVerificationStatus(userId);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error getting verification status: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get verification status"))
                .build();
        }
    }
    
    // ==================== ADMIN ENDPOINTS ====================
    
    @PUT
    @Path("/identity/{userId}/approve")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Approve identity verification", description = "Admin endpoint to approve identity verification")
    public Response approveIdentityVerification(
            @PathParam("userId") UUID userId,
            ApprovalRequest request) {
        
        try {
            UUID adminId = getUserId();
            logger.info("Admin " + adminId + " approving identity verification for user " + userId);
            
            verificationService.approveIdentityVerification(
                userId, 
                adminId, 
                request != null ? request.getNotes() : null
            );
            
            return Response.ok(ApiResponse.success(null, "Identity verification approved")).build();
            
        } catch (Exception e) {
            logger.severe("Error approving identity verification: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to approve identity verification"))
                .build();
        }
    }
    
    @PUT
    @Path("/identity/{userId}/reject")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Reject identity verification", description = "Admin endpoint to reject identity verification")
    public Response rejectIdentityVerification(
            @PathParam("userId") UUID userId,
            @Valid RejectionRequest request) {
        
        try {
            UUID adminId = getUserId();
            logger.info("Admin " + adminId + " rejecting identity verification for user " + userId);
            
            verificationService.rejectIdentityVerification(userId, adminId, request.getReason());
            
            return Response.ok(ApiResponse.success(null, "Identity verification rejected")).build();
            
        } catch (Exception e) {
            logger.severe("Error rejecting identity verification: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to reject identity verification"))
                .build();
        }
    }
    
    @PUT
    @Path("/driver-documents/{userId}/approve")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Approve driver documents", description = "Admin endpoint to approve driver documents")
    public Response approveDriverDocuments(
            @PathParam("userId") UUID userId,
            ApprovalRequest request) {
        
        try {
            UUID adminId = getUserId();
            logger.info("Admin " + adminId + " approving driver documents for user " + userId);
            
            verificationService.approveDriverDocuments(
                userId, 
                adminId, 
                request != null ? request.getNotes() : null
            );
            
            return Response.ok(ApiResponse.success(null, "Driver documents approved")).build();
            
        } catch (Exception e) {
            logger.severe("Error approving driver documents: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to approve driver documents"))
                .build();
        }
    }
    
    @PUT
    @Path("/driver-documents/{userId}/reject")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Reject driver documents", description = "Admin endpoint to reject driver documents")
    public Response rejectDriverDocuments(
            @PathParam("userId") UUID userId,
            @Valid RejectionRequest request) {
        
        try {
            UUID adminId = getUserId();
            logger.info("Admin " + adminId + " rejecting driver documents for user " + userId);
            
            verificationService.rejectDriverDocuments(userId, adminId, request.getReason());
            
            return Response.ok(ApiResponse.success(null, "Driver documents rejected")).build();
            
        } catch (Exception e) {
            logger.severe("Error rejecting driver documents: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to reject driver documents"))
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
}