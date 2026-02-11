package com.commute.metrosync.dto;

import org.jboss.resteasy.reactive.multipart.FileUpload;
import jakarta.ws.rs.FormParam;

/**
 * File Upload Form for Multipart File Uploads
 * Uses Quarkus REST (RESTEasy Reactive) FileUpload class
 * 
 * This is the RECOMMENDED approach for Quarkus 3.x
 * Requires: quarkus-rest-multipart dependency
 * 
 * Usage in Resource:
 * 
 * @POST
 * @Consumes(MediaType.MULTIPART_FORM_DATA)
 * public Response uploadFile(@MultipartForm FileUploadForm form) {
 *     String fileName = form.file.fileName();
 *     Path filePath = form.file.filePath();
 *     long size = form.file.size();
 *     String contentType = form.file.contentType();
 *     
 *     // Process the file...
 *     Files.copy(filePath, targetPath);
 *     
 *     return Response.ok().build();
 * }
 */
public class FileUploadForm {
    
    /**
     * Uploaded file
     * 
     * Access methods:
     * - file.fileName() - Original filename from client
     * - file.filePath() - Temporary file path on server
     * - file.size() - File size in bytes
     * - file.contentType() - MIME type (e.g., "image/jpeg")
     * - file.uploadedFileName() - Name given to temp file on server
     */
    @FormParam("file")
    public FileUpload file;
    
    /**
     * Optional: Additional metadata fields
     */
    @FormParam("description")
    public String description;
    
    @FormParam("category")
    public String category;
    
    // Default constructor
    public FileUploadForm() {
    }
    
    // Helper methods
    
    /**
     * Check if file was uploaded
     */
    public boolean hasFile() {
        return file != null && file.fileName() != null && !file.fileName().isEmpty();
    }
    
    /**
     * Get file extension
     */
    public String getFileExtension() {
        if (file == null || file.fileName() == null) {
            return "";
        }
        String fileName = file.fileName();
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }
    
    /**
     * Check if file is an image
     */
    public boolean isImage() {
        String contentType = file != null ? file.contentType() : "";
        return contentType != null && contentType.startsWith("image/");
    }
    
    /**
     * Check if file is a PDF
     */
    public boolean isPdf() {
        return file != null && "application/pdf".equals(file.contentType());
    }
}