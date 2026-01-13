package com.commute.metrosync.service;

import com.commute.metrosync.entity.LandmarkLocation;
import com.commute.metrosync.repository.LandmarkRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for importing landmarks from CSV file
 * CSV Format: name,category,latitude,longitude,district,description,search_terms
 */
@ApplicationScoped
public class LandmarkImportService {
    
    @Inject
    LandmarkRepository landmarkRepository;
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    /**
     * Import landmarks from CSV file
     * 
     * @param csvInputStream Input stream of CSV file
     * @return Number of landmarks imported
     */
    @Transactional
    public ImportResult importFromCsv(InputStream csvInputStream) {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int skippedCount = 0;
        int lineNumber = 0;
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvInputStream, StandardCharsets.UTF_8))) {
            
            String line;
            boolean isHeader = true;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // Skip header
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    LandmarkLocation landmark = parseCsvLine(line);
                    
                    // Check if landmark already exists (by name and location)
                    if (landmarkExists(landmark)) {
                        skippedCount++;
                        Log.info(String.format("Skipped duplicate: %s", landmark.getName()));
                        continue;
                    }
                    
                    landmarkRepository.persist(landmark);
                    successCount++;
                    Log.info(String.format("Imported: %s (%s)", 
                        landmark.getName(), landmark.getCategory()));
                    
                } catch (Exception e) {
                    String error = String.format("Line %d: %s - %s", 
                        lineNumber, line, e.getMessage());
                    errors.add(error);
                    Log.error(error);
                }
            }
            
        } catch (Exception e) {
            Log.error("Failed to read CSV file", e);
            errors.add("Failed to read CSV: " + e.getMessage());
        }
        
        return new ImportResult(successCount, skippedCount, errors);
    }
    
    /**
     * Parse a single CSV line into a LandmarkLocation entity
     */
    private LandmarkLocation parseCsvLine(String line) {
        // Simple CSV parsing (handles quoted fields)
        String[] parts = parseCsvLineAdvanced(line);
        
        if (parts.length < 4) {
            throw new IllegalArgumentException(
                "Invalid CSV format. Expected at least: name,category,latitude,longitude");
        }
        
        String name = parts[0].trim();
        String category = parts[1].trim().toUpperCase();
        double latitude = Double.parseDouble(parts[2].trim());
        double longitude = Double.parseDouble(parts[3].trim());
        
        // Optional fields
        String district = parts.length > 4 ? parts[4].trim() : null;
        String description = parts.length > 5 ? parts[5].trim() : null;
        String searchTermsStr = parts.length > 6 ? parts[6].trim() : null;
        
        // Create point geometry (lon, lat order for PostGIS)
        Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        location.setSRID(4326);
        
        // Parse search terms (comma-separated)
        String[] searchTerms = null;
        if (searchTermsStr != null && !searchTermsStr.isEmpty()) {
            searchTerms = searchTermsStr.split(",");
            // Trim each term
            for (int i = 0; i < searchTerms.length; i++) {
                searchTerms[i] = searchTerms[i].trim().toLowerCase();
            }
        }
        
        LandmarkLocation landmark = new LandmarkLocation(
            name,
            category,
            location,
            district
        );
        
        if (description != null && !description.isEmpty()) {
            landmark.setDescription(description);
        }
        
        if (searchTerms != null) {
            landmark.setSearchTerms(searchTerms);
        }
        
        return landmark;
    }
    
    /**
     * Advanced CSV parsing that handles quoted fields
     */
    private String[] parseCsvLineAdvanced(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        // Add last field
        fields.add(currentField.toString());
        
        return fields.toArray(new String[0]);
    }
    
    /**
     * Check if landmark already exists (by name)
     */
    private boolean landmarkExists(LandmarkLocation landmark) {
        return landmarkRepository.findByName(landmark.getName()).isPresent();
    }
    
    /**
     * Export landmarks to CSV format
     */
    public String exportToCsv() {
        List<LandmarkLocation> landmarks = landmarkRepository.findAllActive();
        
        StringBuilder csv = new StringBuilder();
        csv.append("name,category,latitude,longitude,district,description,search_terms\n");
        
        for (LandmarkLocation landmark : landmarks) {
            csv.append(escapeCsv(landmark.getName())).append(",");
            csv.append(landmark.getCategory()).append(",");
            csv.append(landmark.getLocation().getY()).append(","); // latitude
            csv.append(landmark.getLocation().getX()).append(","); // longitude
            csv.append(escapeCsv(landmark.getDistrict())).append(",");
            csv.append(escapeCsv(landmark.getDescription())).append(",");
            
            // Join search terms
            if (landmark.getSearchTerms() != null) {
                csv.append("\"").append(String.join(",", landmark.getSearchTerms())).append("\"");
            }
            
            csv.append("\n");
        }
        
        return csv.toString();
    }
    
    /**
     * Escape CSV field (wrap in quotes if contains comma or newline)
     */
    private String escapeCsv(String field) {
        if (field == null || field.isEmpty()) {
            return "";
        }
        
        if (field.contains(",") || field.contains("\n") || field.contains("\"")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        
        return field;
    }
    
    /**
     * Result of CSV import operation
     */
    public record ImportResult(
        int successCount,
        int skippedCount,
        List<String> errors
    ) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public int totalProcessed() {
            return successCount + skippedCount;
        }
    }
}