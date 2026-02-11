package com.commute.metrosync.service;

import com.google.cloud.storage.*;
import com.google.firebase.cloud.StorageClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Firebase Storage Service
 * Alternative to AWS S3 for file storage
 */
@ApplicationScoped
public class FirebaseStorageService {

    private static final Logger LOG = Logger.getLogger(FirebaseStorageService.class);

    @ConfigProperty(name = "firebase.storage.bucket")
    String bucketName;

    @Inject
    com.commute.metrosync.config.FirebaseConfig firebaseConfig;

    /**
     * Upload file to Firebase Storage
     * 
     * @param fileData File byte array
     * @param fileName File name
     * @param contentType MIME type
     * @param folder Folder path (e.g., "profiles", "documents")
     * @return Public download URL
     */
    public String uploadFile(byte[] fileData, String fileName, String contentType, String folder) {
        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            
            // Generate unique file path
            String filePath = generateFilePath(folder, fileName);
            
            // Create blob
            BlobId blobId = BlobId.of(bucket.getName(), filePath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .build();

            // Upload file
            Blob blob = bucket.getStorage().create(blobInfo, fileData);
            
            LOG.infof("File uploaded successfully: %s", filePath);
            
            // Return public URL
            return String.format("https://storage.googleapis.com/%s/%s", 
                                bucket.getName(), filePath);

        } catch (Exception e) {
            LOG.errorf("Failed to upload file to Firebase Storage: %s", e.getMessage());
            throw new RuntimeException("File upload failed", e);
        }
    }

    /**
     * Upload file with input stream
     */
    public String uploadFile(InputStream inputStream, String fileName, 
                           String contentType, String folder) throws IOException {
        byte[] fileData = inputStream.readAllBytes();
        return uploadFile(fileData, fileName, contentType, folder);
    }

    /**
     * Upload profile image
     */
    public String uploadProfileImage(byte[] imageData, String userId, String extension) {
        String fileName = String.format("%s-profile.%s", userId, extension);
        String contentType = getContentTypeFromExtension(extension);
        return uploadFile(imageData, fileName, contentType, "profiles");
    }

    /**
     * Upload driver document
     */
    public String uploadDriverDocument(byte[] documentData, String userId, 
                                      String documentType, String extension) {
        String fileName = String.format("%s-%s.%s", userId, documentType, extension);
        String contentType = getContentTypeFromExtension(extension);
        return uploadFile(documentData, fileName, contentType, "documents/drivers");
    }

    /**
     * Upload vehicle document
     */
    public String uploadVehicleDocument(byte[] documentData, String vehicleId, 
                                       String documentType, String extension) {
        String fileName = String.format("%s-%s.%s", vehicleId, documentType, extension);
        String contentType = getContentTypeFromExtension(extension);
        return uploadFile(documentData, fileName, contentType, "documents/vehicles");
    }

    /**
     * Delete file from Firebase Storage
     */
    public boolean deleteFile(String fileUrl) {
        try {
            // Extract file path from URL
            String filePath = extractFilePathFromUrl(fileUrl);
            
            Bucket bucket = StorageClient.getInstance().bucket();
            BlobId blobId = BlobId.of(bucket.getName(), filePath);
            
            boolean deleted = bucket.getStorage().delete(blobId);
            
            if (deleted) {
                LOG.infof("File deleted successfully: %s", filePath);
            } else {
                LOG.warnf("File not found or already deleted: %s", filePath);
            }
            
            return deleted;

        } catch (Exception e) {
            LOG.errorf("Failed to delete file from Firebase Storage: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Generate signed URL for temporary access
     * Useful for private files that need temporary public access
     * 
     * @param filePath Path to file in storage
     * @param expirationMinutes How long the URL should be valid
     * @return Signed URL
     */
    public String generateSignedUrl(String filePath, int expirationMinutes) {
        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            BlobId blobId = BlobId.of(bucket.getName(), filePath);
            Blob blob = bucket.getStorage().get(blobId);

            if (blob == null) {
                throw new IllegalArgumentException("File not found: " + filePath);
            }

            URL signedUrl = blob.signUrl(expirationMinutes, TimeUnit.MINUTES);
            return signedUrl.toString();

        } catch (Exception e) {
            LOG.errorf("Failed to generate signed URL: %s", e.getMessage());
            throw new RuntimeException("Failed to generate signed URL", e);
        }
    }

    /**
     * Get file metadata
     */
    public FileMetadata getFileMetadata(String filePath) {
        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            BlobId blobId = BlobId.of(bucket.getName(), filePath);
            Blob blob = bucket.getStorage().get(blobId);

            if (blob == null) {
                return null;
            }

            return new FileMetadata(
                blob.getName(),
                blob.getContentType(),
                blob.getSize(),
                blob.getCreateTime(),
                blob.getUpdateTime(),
                blob.getMediaLink()
            );

        } catch (Exception e) {
            LOG.errorf("Failed to get file metadata: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Check if file exists
     */
    public boolean fileExists(String filePath) {
        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            BlobId blobId = BlobId.of(bucket.getName(), filePath);
            Blob blob = bucket.getStorage().get(blobId);
            return blob != null && blob.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Download file as byte array
     */
    public byte[] downloadFile(String filePath) {
        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            BlobId blobId = BlobId.of(bucket.getName(), filePath);
            Blob blob = bucket.getStorage().get(blobId);

            if (blob == null) {
                throw new IllegalArgumentException("File not found: " + filePath);
            }

            return blob.getContent();

        } catch (Exception e) {
            LOG.errorf("Failed to download file: %s", e.getMessage());
            throw new RuntimeException("Failed to download file", e);
        }
    }

    /**
     * Copy file to new location
     */
    public String copyFile(String sourceFilePath, String destinationFolder, String newFileName) {
        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            
            // Source blob
            BlobId sourceBlobId = BlobId.of(bucket.getName(), sourceFilePath);
            Blob sourceBlob = bucket.getStorage().get(sourceBlobId);
            
            if (sourceBlob == null) {
                throw new IllegalArgumentException("Source file not found: " + sourceFilePath);
            }

            // Destination blob
            String destinationPath = generateFilePath(destinationFolder, newFileName);
            BlobId destinationBlobId = BlobId.of(bucket.getName(), destinationPath);

            // Copy
            bucket.getStorage().copy(
                Storage.CopyRequest.newBuilder()
                    .setSource(sourceBlobId)
                    .setTarget(destinationBlobId)
                    .build()
            );

            LOG.infof("File copied: %s -> %s", sourceFilePath, destinationPath);

            return String.format("https://storage.googleapis.com/%s/%s", 
                                bucket.getName(), destinationPath);

        } catch (Exception e) {
            LOG.errorf("Failed to copy file: %s", e.getMessage());
            throw new RuntimeException("Failed to copy file", e);
        }
    }

    // ==================== HELPER METHODS ====================

    private String generateFilePath(String folder, String fileName) {
        String sanitizedFileName = sanitizeFileName(fileName);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        
        return String.format("%s/%s-%s-%s", 
                           folder, timestamp, uniqueId, sanitizedFileName);
    }

    private String sanitizeFileName(String fileName) {
        // Remove special characters, keep only alphanumeric, dots, dashes, underscores
        return fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private String extractFilePathFromUrl(String fileUrl) {
        // Extract file path from Google Storage URL
        // Format: https://storage.googleapis.com/bucket-name/file/path
        String prefix = "https://storage.googleapis.com/" + bucketName + "/";
        if (fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }
        
        // If it's already a file path, return as-is
        return fileUrl;
    }

    private String getContentTypeFromExtension(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    // ==================== RESPONSE CLASSES ====================

    public record FileMetadata(
        String name,
        String contentType,
        Long size,
        Long createdAt,
        Long updatedAt,
        String downloadUrl
    ) {}
}