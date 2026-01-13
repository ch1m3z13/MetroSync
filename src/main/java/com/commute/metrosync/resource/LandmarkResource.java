package com.commute.metrosync.resource;

import com.commute.metrosync.dto.ErrorResponse;
import com.commute.metrosync.entity.LandmarkLocation;
import com.commute.metrosync.repository.LandmarkRepository;
import com.commute.metrosync.service.LandmarkImportService;
import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.FileInputStream;
import java.util.List;
import java.util.UUID;

@Path("/landmarks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Landmarks", description = "Curated landmark locations for driver signup")
public class LandmarkResource {
    
    @Inject
    LandmarkRepository landmarkRepository;
    
    @Inject
    LandmarkImportService importService;
    
    /**
     * PUBLIC: Get all landmarks (for dropdowns during signup)
     */
    @GET
    @PermitAll
    @Operation(
        summary = "Get all landmarks",
        description = "Returns all active landmarks for driver signup"
    )
    public Response getAllLandmarks(
            @QueryParam("category") String category,
            @QueryParam("district") String district) {
        
        List<LandmarkLocation> landmarks;
        
        if (category != null && !category.isEmpty()) {
            landmarks = landmarkRepository.findByCategory(category);
        } else if (district != null && !district.isEmpty()) {
            landmarks = landmarkRepository.findByDistrict(district);
        } else {
            landmarks = landmarkRepository.findAllActive();
        }
        
        List<LandmarkDTO> dtos = landmarks.stream()
            .map(this::toDTO)
            .toList();
        
        return Response.ok(dtos).build();
    }
    
    /**
     * PUBLIC: Search landmarks by text
     */
    @GET
    @Path("/search")
    @PermitAll
    @Operation(
        summary = "Search landmarks",
        description = "Search landmarks by name or search terms (fuzzy matching)"
    )
    public Response searchLandmarks(
            @QueryParam("q") String query,
            @QueryParam("limit") @DefaultValue("20") Integer limit) {
        
        if (query == null || query.trim().isEmpty()) {
            return Response.status(400)
                .entity(new ErrorResponse("Query parameter 'q' is required"))
                .build();
        }
        
        List<LandmarkRepository.LandmarkSearchResult> results = 
            landmarkRepository.searchByText(query, limit);
        
        return Response.ok(results).build();
    }
    
    /**
     * PUBLIC: Find landmarks near coordinates
     */
    @GET
    @Path("/nearby")
    @PermitAll
    @Operation(
        summary = "Find nearby landmarks",
        description = "Find landmarks within radius of given coordinates"
    )
    public Response findNearbyLandmarks(
            @QueryParam("lat") Double latitude,
            @QueryParam("lng") Double longitude,
            @QueryParam("radius") @DefaultValue("1000") Double radiusMeters,
            @QueryParam("category") String category,
            @QueryParam("limit") @DefaultValue("10") Integer limit) {
        
        if (latitude == null || longitude == null) {
            return Response.status(400)
                .entity(new ErrorResponse("Both 'lat' and 'lng' are required"))
                .build();
        }
        
        List<LandmarkRepository.LandmarkSearchResult> results = 
            landmarkRepository.findNearPoint(
                latitude, longitude, radiusMeters, category, limit
            );
        
        return Response.ok(results).build();
    }
    
    /**
     * PUBLIC: Get popular landmarks
     */
    @GET
    @Path("/popular")
    @PermitAll
    @Operation(
        summary = "Get popular landmarks",
        description = "Returns most frequently selected landmarks"
    )
    public Response getPopularLandmarks(
            @QueryParam("limit") @DefaultValue("50") Integer limit) {
        
        List<LandmarkLocation> landmarks = landmarkRepository.findMostPopular(limit);
        
        List<LandmarkDTO> dtos = landmarks.stream()
            .map(this::toDTO)
            .toList();
        
        return Response.ok(dtos).build();
    }
    
    /**
     * PUBLIC: Get landmark details by ID
     */
    @GET
    @Path("/{landmarkId}")
    @PermitAll
    @Operation(summary = "Get landmark details")
    public Response getLandmark(@PathParam("landmarkId") UUID landmarkId) {
        LandmarkLocation landmark = landmarkRepository.findByIdOptional(landmarkId)
            .orElseThrow(() -> new NotFoundException("Landmark not found"));
        
        return Response.ok(toDTO(landmark)).build();
    }
    
    /**
     * ADMIN: Import landmarks from CSV
     */
    @POST
    @Path("/import")
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(
        summary = "Import landmarks from CSV (Admin only)",
        description = "Upload CSV file to bulk-import landmarks"
    )
    public Response importLandmarks(@RestForm("file") FileUpload file) {
        try {
            if (file == null) {
                return Response.status(400)
                    .entity(new ErrorResponse("No file uploaded"))
                    .build();
            }
            
            // Validate file type
            String filename = file.fileName();
            if (!filename.endsWith(".csv")) {
                return Response.status(400)
                    .entity(new ErrorResponse("Only CSV files are allowed"))
                    .build();
            }
            
            // Import from CSV
            try (FileInputStream fis = new FileInputStream(file.uploadedFile().toFile())) {
                LandmarkImportService.ImportResult result = importService.importFromCsv(fis);
                
                Log.info(String.format(
                    "Import completed: %d success, %d skipped, %d errors",
                    result.successCount(), result.skippedCount(), result.errors().size()
                ));
                
                return Response.ok(result).build();
            }
            
        } catch (Exception e) {
            Log.error("Failed to import landmarks", e);
            return Response.serverError()
                .entity(new ErrorResponse("Import failed: " + e.getMessage()))
                .build();
        }
    }
    
    /**
     * ADMIN: Export landmarks to CSV
     */
    @GET
    @Path("/export")
    @RolesAllowed("ADMIN")
    @Produces("text/csv")
    @Operation(
        summary = "Export landmarks to CSV (Admin only)",
        description = "Download all landmarks as CSV file"
    )
    public Response exportLandmarks() {
        try {
            String csv = importService.exportToCsv();
            
            return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=landmarks.csv")
                .build();
            
        } catch (Exception e) {
            Log.error("Failed to export landmarks", e);
            return Response.serverError()
                .entity(new ErrorResponse("Export failed: " + e.getMessage()))
                .build();
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private LandmarkDTO toDTO(LandmarkLocation landmark) {
        return new LandmarkDTO(
            landmark.getId().toString(),
            landmark.getName(),
            landmark.getCategory().name(),
            landmark.getDistrict(),
            landmark.getDescription(),
            landmark.getLocation().getY(), // latitude
            landmark.getLocation().getX(), // longitude
            landmark.getSearchTerms(),
            landmark.getPopularityScore()
        );
    }
    
    // ==================== DTOs ====================
    
    public record LandmarkDTO(
        String id,
        String name,
        String category,
        String district,
        String description,
        double latitude,
        double longitude,
        String[] searchTerms,
        int popularityScore
    ) {}
}