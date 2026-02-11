package com.commute.metrosync.dto;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.annotations.providers.multipart.PartType;

import java.io.InputStream;

/**
 * File Upload Form for Multipart File Uploads
 * Used with Jakarta EE / JAX-RS @MultipartForm annotation
 */
public class FileUploadForm {
    
    private InputStream file;
    private String fileName;
    private String contentType;
    
    @FormParam("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public InputStream getFile() {
        return file;
    }
    
    public void setFile(InputStream file) {
        this.file = file;
    }
    
    @FormParam("file")
    @PartType(MediaType.TEXT_PLAIN)
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    @FormParam("file")
    @PartType(MediaType.TEXT_PLAIN)
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}