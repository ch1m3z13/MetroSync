package com.commute.metrosync.resource;

import com.commute.metrosync.dto.response.ApiResponse;
import com.commute.metrosync.service.FileStorageService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * File Upload Resource - API endpoints for document uploads
 */
@Path("/api/upload")
@Produces(MediaType.APPLICATION_JSON)
public class FileUploadResource {

    private static final Logger LOG = Logger.getLogger(FileUploadResource.class);

    @Inject
    FileStorageService fileStorageService;

    /**
     * Upload profile picture
     * POST /api/upload/profile
     */
    @POST
    @Path("/profile")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"RIDER", "DRIVER"})
    public ApiResponse<Map<String, String>> uploadProfilePicture(
        @RestForm("file") FileUpload file
    ) {
        return uploadImage(file, FileStorageService.ImageType.PROFILE_PICTURE);
    }

    /**
     * Upload selfie for verification
     * POST /api/upload/selfie
     */
    @POST
    @Path("/selfie")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"RIDER", "DRIVER"})
    public ApiResponse<Map<String, String>> uploadSelfie(
        @RestForm("file") FileUpload file
    ) {
        return uploadImage(file, FileStorageService.ImageType.SELFIE);
    }

    /**
     * Upload company ID card
     * POST /api/upload/company-id
     */
    @POST
    @Path("/company-id")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("RIDER")
    public ApiResponse<Map<String, String>> uploadCompanyId(
        @RestForm("file") FileUpload file
    ) {
        return uploadImage(file, FileStorageService.ImageType.COMPANY_ID);
    }

    /**
     * Upload employment letter
     * POST /api/upload/employment-letter
     */
    @POST
    @Path("/employment-letter")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("RIDER")
    public ApiResponse<Map<String, String>> uploadEmploymentLetter(
        @RestForm("file") FileUpload file
    ) {
        return uploadImage(file, FileStorageService.ImageType.EMPLOYMENT_LETTER);
    }

    /**
     * Upload driver's license (front)
     * POST /api/upload/license-front
     */
    @POST
    @Path("/license-front")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("DRIVER")
    public ApiResponse<Map<String, String>> uploadLicenseFront(
        @RestForm("file") FileUpload file
    ) {
        return uploadImage(file, FileStorageService.ImageType.LICENSE_FRONT);
    }

    /**
     * Upload driver's license (back)
     * POST /api/upload/license-back
     */
    @POST
    @Path("/license-back")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("DRIVER")
    public ApiResponse<Map<String, String>> uploadLicenseBack(
        @RestForm("file") FileUpload file
    ) {
        return uploadImage(file, FileStorageService.ImageType.LICENSE_BACK);
    }

    /**
     * Upload vehicle registration
     * POST /api/upload/vehicle-registration
     */
    @POST
    @Path("/vehicle-registration")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("DRIVER")
    public ApiResponse<Map<String, String>> uploadVehicleRegistration(
        @RestForm("file") FileUpload file
    ) {
        return uploadImage(file, FileStorageService.ImageType.VEHICLE_REGISTRATION);
    }

    /**
     * Upload insurance document
     * POST /api/upload/insurance
     */
    @POST
    @Path("/insurance")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("DRIVER")
    public ApiResponse<Map<String, String>> uploadInsurance(
        @RestForm("file") FileUpload file
    ) {
        return uploadImage(file, FileStorageService.ImageType.INSURANCE_DOCUMENT);
    }

    /**
     * Upload police clearance
     * POST /api/upload/police-clearance
     */
    @POST
    @Path("/police-clearance")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("DRIVER")
    public ApiResponse<Map<String, String>> uploadPoliceClearance(
        @RestForm("file") FileUpload file
    ) {
        return uploadImage(file, FileStorageService.ImageType.POLICE_CLEARANCE);
    }

    /**
     * Upload vehicle photo
     * POST /api/upload/vehicle-photo
     */
    @POST
    @Path("/vehicle-photo")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("DRIVER")
    public ApiResponse<Map<String, String>> uploadVehiclePhoto(
        @RestForm("file") FileUpload file
    ) {
        return uploadImage(file, FileStorageService.ImageType.VEHICLE_PHOTO);
    }

    /**
     * Generic image upload handler
     */
    private ApiResponse<Map<String, String>> uploadImage(
        FileUpload file,
        FileStorageService.ImageType imageType
    ) {
        try {
            if (file == null) {
                return ApiResponse.error("No file provided");
            }

            // Get file info
            String fileName = file.fileName();
            String contentType = file.contentType();
            long fileSize = file.size();

            // Upload file
            try (InputStream fileStream = new FileInputStream(file.uploadedFile().toFile())) {
                FileStorageService.FileUploadResult result = fileStorageService.uploadImage(
                    fileStream,
                    fileName,
                    contentType,
                    fileSize,
                    imageType
                );

                if (result.success) {
                    return ApiResponse.success(
                        result.message,
                        Map.of("fileUrl", result.fileUrl)
                    );
                } else {
                    return ApiResponse.error(result.message);
                }
            }
        } catch (IOException e) {
            LOG.error("Error uploading file", e);
            return ApiResponse.error("File upload failed");
        }
    }

    /**
     * Delete file
     * DELETE /api/upload
     */
    @DELETE
    @RolesAllowed({"RIDER", "DRIVER"})
    public ApiResponse<String> deleteFile(@QueryParam("fileUrl") String fileUrl) {
        try {
            if (fileUrl == null || fileUrl.isEmpty()) {
                return ApiResponse.error("File URL is required");
            }

            boolean deleted = fileStorageService.deleteFile(fileUrl);
            if (deleted) {
                return ApiResponse.success("File deleted successfully");
            } else {
                return ApiResponse.error("Failed to delete file");
            }
        } catch (Exception e) {
            LOG.error("Error deleting file", e);
            return ApiResponse.error("File deletion failed");
        }
    }
}