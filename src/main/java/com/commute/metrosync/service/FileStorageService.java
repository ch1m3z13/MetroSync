package com.commute.metrosync.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * File Storage Service
 * Handles document uploads for verification (NIN, selfies, driver documents)
 * Supports both S3 (production) and local storage (development)
 */
@Service
public class FileStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);
    
    @Value("${file.storage.type:s3}") // s3 or local
    private String storageType;
    
    @Value("${file.storage.local.path:./uploads}")
    private String localStoragePath;
    
    @Value("${aws.s3.bucket.name}")
    private String s3BucketName;
    
    @Value("${aws.s3.region:us-east-1}")
    private String s3Region;
    
    @Value("${file.storage.max.size:10485760}") // 10MB default
    private long maxFileSize;
    
    private final AmazonS3 amazonS3;
    
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    
    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
        "application/pdf", "image/jpeg", "image/jpg", "image/png"
    );
    
    public FileStorageService(AmazonS3 amazonS3) {
        this.amazonS3 = amazonS3;
    }
    
    /**
     * Upload a file to storage
     * 
     * @param file Multipart file from request
     * @param folder Folder path (e.g., "nin", "selfies", "driver-licenses")
     * @param userId User ID for organizing files
     * @return FileUploadResponse with file URL and metadata
     */
    public FileUploadResponse uploadFile(MultipartFile file, String folder, UUID userId) {
        try {
            // Validate file
            FileValidationResult validation = validateFile(file);
            if (!validation.isValid()) {
                return FileUploadResponse.error(validation.getErrorMessage());
            }
            
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String filename = generateFilename(userId, extension);
            String fullPath = folder + "/" + filename;
            
            // Upload based on storage type
            String fileUrl;
            if ("s3".equalsIgnoreCase(storageType)) {
                fileUrl = uploadToS3(file, fullPath);
            } else {
                fileUrl = uploadToLocal(file, fullPath);
            }
            
            if (fileUrl != null) {
                logger.info("File uploaded successfully: {}", fullPath);
                return FileUploadResponse.success(
                    fileUrl,
                    filename,
                    fullPath,
                    file.getSize(),
                    file.getContentType()
                );
            } else {
                return FileUploadResponse.error("Failed to upload file");
            }
            
        } catch (Exception e) {
            logger.error("Error uploading file", e);
            return FileUploadResponse.error("Upload failed: " + e.getMessage());
        }
    }
    
    /**
     * Upload NIN document
     */
    public FileUploadResponse uploadNinDocument(MultipartFile file, UUID userId) {
        return uploadFile(file, "verification/nin", userId);
    }
    
    /**
     * Upload selfie for verification
     */
    public FileUploadResponse uploadSelfie(MultipartFile file, UUID userId) {
        return uploadFile(file, "verification/selfies", userId);
    }
    
    /**
     * Upload driver's license
     */
    public FileUploadResponse uploadDriverLicense(MultipartFile file, UUID userId) {
        return uploadFile(file, "verification/driver-licenses", userId);
    }
    
    /**
     * Upload vehicle registration
     */
    public FileUploadResponse uploadVehicleRegistration(MultipartFile file, UUID userId) {
        return uploadFile(file, "verification/vehicle-registration", userId);
    }
    
    /**
     * Upload vehicle insurance
     */
    public FileUploadResponse uploadVehicleInsurance(MultipartFile file, UUID userId) {
        return uploadFile(file, "verification/vehicle-insurance", userId);
    }
    
    /**
     * Upload vehicle roadworthiness certificate
     */
    public FileUploadResponse uploadRoadworthiness(MultipartFile file, UUID userId) {
        return uploadFile(file, "verification/roadworthiness", userId);
    }
    
    /**
     * Upload vehicle photo
     */
    public FileUploadResponse uploadVehiclePhoto(MultipartFile file, UUID userId, String photoType) {
        // photoType: front, back, side, interior
        return uploadFile(file, "verification/vehicle-photos/" + photoType, userId);
    }
    
    /**
     * Delete a file from storage
     * 
     * @param filePath Full file path
     * @return true if deleted successfully
     */
    public boolean deleteFile(String filePath) {
        try {
            if ("s3".equalsIgnoreCase(storageType)) {
                return deleteFromS3(filePath);
            } else {
                return deleteFromLocal(filePath);
            }
        } catch (Exception e) {
            logger.error("Error deleting file: {}", filePath, e);
            return false;
        }
    }
    
    /**
     * Generate a pre-signed URL for temporary file access
     * (S3 only)
     * 
     * @param filePath File path in S3
     * @param expirationMinutes URL expiration in minutes
     * @return Pre-signed URL
     */
    public String generatePresignedUrl(String filePath, int expirationMinutes) {
        if (!"s3".equalsIgnoreCase(storageType)) {
            logger.warn("Pre-signed URLs only available for S3 storage");
            return null;
        }
        
        try {
            Date expiration = new Date(System.currentTimeMillis() + (expirationMinutes * 60 * 1000));
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(s3BucketName, filePath)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration);
            
            URL url = amazonS3.generatePresignedUrl(request);
            return url.toString();
            
        } catch (Exception e) {
            logger.error("Error generating pre-signed URL", e);
            return null;
        }
    }
    
    /**
     * Get file metadata
     * 
     * @param filePath File path
     * @return FileMetadata with size, content type, etc.
     */
    public FileMetadata getFileMetadata(String filePath) {
        try {
            if ("s3".equalsIgnoreCase(storageType)) {
                return getS3Metadata(filePath);
            } else {
                return getLocalMetadata(filePath);
            }
        } catch (Exception e) {
            logger.error("Error getting file metadata", e);
            return null;
        }
    }
    
    // ==================== PRIVATE METHODS ====================
    
    private FileValidationResult validateFile(MultipartFile file) {
        // Check if file is empty
        if (file.isEmpty()) {
            return FileValidationResult.invalid("File is empty");
        }
        
        // Check file size
        if (file.getSize() > maxFileSize) {
            return FileValidationResult.invalid(
                String.format("File size exceeds maximum allowed size of %d MB", maxFileSize / (1024 * 1024))
            );
        }
        
        // Check content type
        String contentType = file.getContentType();
        if (contentType == null) {
            return FileValidationResult.invalid("Unable to determine file type");
        }
        
        if (!ALLOWED_IMAGE_TYPES.contains(contentType) && !ALLOWED_DOCUMENT_TYPES.contains(contentType)) {
            return FileValidationResult.invalid(
                "Invalid file type. Allowed types: JPEG, PNG, WEBP, PDF"
            );
        }
        
        return FileValidationResult.valid();
    }
    
    private String generateFilename(UUID userId, String extension) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String randomString = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s_%s_%s%s", userId, timestamp, randomString, extension);
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
    
    // ==================== S3 OPERATIONS ====================
    
    private String uploadToS3(MultipartFile file, String fullPath) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            metadata.setCacheControl("max-age=31536000"); // 1 year cache
            
            // Upload to S3
            PutObjectRequest request = new PutObjectRequest(
                s3BucketName,
                fullPath,
                file.getInputStream(),
                metadata
            );
            
            // Set public read access (optional - adjust based on security requirements)
            request.withCannedAcl(CannedAccessControlList.Private);
            
            amazonS3.putObject(request);
            
            // Return S3 URL
            return String.format("https://%s.s3.%s.amazonaws.com/%s", s3BucketName, s3Region, fullPath);
            
        } catch (Exception e) {
            logger.error("Error uploading to S3", e);
            return null;
        }
    }
    
    private boolean deleteFromS3(String filePath) {
        try {
            amazonS3.deleteObject(s3BucketName, filePath);
            logger.info("Deleted file from S3: {}", filePath);
            return true;
        } catch (Exception e) {
            logger.error("Error deleting from S3", e);
            return false;
        }
    }
    
    private FileMetadata getS3Metadata(String filePath) {
        try {
            ObjectMetadata metadata = amazonS3.getObjectMetadata(s3BucketName, filePath);
            
            return new FileMetadata(
                filePath,
                metadata.getContentLength(),
                metadata.getContentType(),
                metadata.getLastModified(),
                "s3"
            );
        } catch (Exception e) {
            logger.error("Error getting S3 metadata", e);
            return null;
        }
    }
    
    // ==================== LOCAL STORAGE OPERATIONS ====================
    
    private String uploadToLocal(MultipartFile file, String fullPath) {
        try {
            // Create directory if it doesn't exist
            Path uploadPath = Paths.get(localStoragePath, fullPath).getParent();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            // Save file
            Path filePath = Paths.get(localStoragePath, fullPath);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            // Return local URL (adjust based on your setup)
            return "/uploads/" + fullPath;
            
        } catch (Exception e) {
            logger.error("Error uploading to local storage", e);
            return null;
        }
    }
    
    private boolean deleteFromLocal(String filePath) {
        try {
            Path path = Paths.get(localStoragePath, filePath);
            Files.deleteIfExists(path);
            logger.info("Deleted file from local storage: {}", filePath);
            return true;
        } catch (Exception e) {
            logger.error("Error deleting from local storage", e);
            return false;
        }
    }
    
    private FileMetadata getLocalMetadata(String filePath) {
        try {
            Path path = Paths.get(localStoragePath, filePath);
            
            if (!Files.exists(path)) {
                return null;
            }
            
            long size = Files.size(path);
            String contentType = Files.probeContentType(path);
            Date lastModified = new Date(Files.getLastModifiedTime(path).toMillis());
            
            return new FileMetadata(
                filePath,
                size,
                contentType,
                lastModified,
                "local"
            );
        } catch (Exception e) {
            logger.error("Error getting local file metadata", e);
            return null;
        }
    }
    
    // ==================== HELPER CLASSES ====================
    
    public static class FileUploadResponse {
        private boolean success;
        private String fileUrl;
        private String filename;
        private String filePath;
        private Long fileSize;
        private String contentType;
        private String errorMessage;
        
        private FileUploadResponse() {}
        
        public static FileUploadResponse success(
                String fileUrl, 
                String filename, 
                String filePath, 
                Long fileSize, 
                String contentType) {
            FileUploadResponse response = new FileUploadResponse();
            response.success = true;
            response.fileUrl = fileUrl;
            response.filename = filename;
            response.filePath = filePath;
            response.fileSize = fileSize;
            response.contentType = contentType;
            return response;
        }
        
        public static FileUploadResponse error(String errorMessage) {
            FileUploadResponse response = new FileUploadResponse();
            response.success = false;
            response.errorMessage = errorMessage;
            return response;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getFileUrl() { return fileUrl; }
        public String getFilename() { return filename; }
        public String getFilePath() { return filePath; }
        public Long getFileSize() { return fileSize; }
        public String getContentType() { return contentType; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class FileValidationResult {
        private boolean valid;
        private String errorMessage;
        
        private FileValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static FileValidationResult valid() {
            return new FileValidationResult(true, null);
        }
        
        public static FileValidationResult invalid(String errorMessage) {
            return new FileValidationResult(false, errorMessage);
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class FileMetadata {
        private String filePath;
        private Long size;
        private String contentType;
        private Date lastModified;
        private String storageType;
        
        public FileMetadata(String filePath, Long size, String contentType, Date lastModified, String storageType) {
            this.filePath = filePath;
            this.size = size;
            this.contentType = contentType;
            this.lastModified = lastModified;
            this.storageType = storageType;
        }
        
        // Getters
        public String getFilePath() { return filePath; }
        public Long getSize() { return size; }
        public String getContentType() { return contentType; }
        public Date getLastModified() { return lastModified; }
        public String getStorageType() { return storageType; }
    }
}