package com.commute.metrosync.repository;

import com.commute.metrosync.entity.LandmarkLocation;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LandmarkRepository implements PanacheRepositoryBase<LandmarkLocation, UUID> {
    
    /**
     * Find landmark by name (for duplicate checking during import)
     */
    public Optional<LandmarkLocation> findByName(String name) {
        return find("LOWER(name) = LOWER(?1) and isActive = true", name)
            .firstResultOptional();
    }
    
    /**
     * Find all active landmarks
     */
    public List<LandmarkLocation> findAllActive() {
        return find("isActive = true order by name")
            .list();
    }
    
    /**
     * Find landmarks by category
     */
    public List<LandmarkLocation> findByCategory(String category) {
        return find("category = ?1 and isActive = true order by popularityScore desc, name", 
            category.toUpperCase())
            .list();
    }
    
    /**
     * Find landmarks by district
     */
    public List<LandmarkLocation> findByDistrict(String district) {
        return find("LOWER(district) = LOWER(?1) and isActive = true order by popularityScore desc, name", 
            district)
            .list();
    }
    
    /**
     * Search landmarks by text (name or search terms)
     */
    @SuppressWarnings("unchecked")
    public List<LandmarkSearchResult> searchByText(String searchText, Integer limit) {
        String sql = """
            SELECT 
                id,
                name,
                category::text,
                district,
                ST_Y(location) as latitude,
                ST_X(location) as longitude,
                description,
                popularity_score,
                GREATEST(
                    CASE WHEN LOWER(name) = LOWER(:search) THEN 1.0 ELSE 0.0 END,
                    CASE WHEN LOWER(name) LIKE LOWER(:search) || '%' THEN 0.8 ELSE 0.0 END,
                    CASE WHEN LOWER(name) LIKE '%' || LOWER(:search) || '%' THEN 0.6 ELSE 0.0 END,
                    CASE WHEN EXISTS (
                        SELECT 1 FROM unnest(search_terms) term 
                        WHERE term LIKE '%' || LOWER(:search) || '%'
                    ) THEN 0.4 ELSE 0.0 END
                ) as relevance_score
            FROM landmark_locations
            WHERE is_active = true
            AND (
                LOWER(name) LIKE '%' || LOWER(:search) || '%'
                OR EXISTS (
                    SELECT 1 FROM unnest(search_terms) term 
                    WHERE term LIKE '%' || LOWER(:search) || '%'
                )
            )
            ORDER BY relevance_score DESC, popularity_score DESC, name ASC
            LIMIT :limit
        """;
        
        List<Object[]> results = getEntityManager()
            .createNativeQuery(sql)
            .setParameter("search", searchText)
            .setParameter("limit", limit != null ? limit : 20)
            .getResultList();
        
        return results.stream()
            .map(row -> new LandmarkSearchResult(
                (UUID) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                ((Number) row[4]).doubleValue(),
                ((Number) row[5]).doubleValue(),
                (String) row[6],
                ((Number) row[7]).intValue(),
                ((Number) row[8]).floatValue()
            ))
            .toList();
    }
    
    /**
     * Find landmarks near a point
     */
    @SuppressWarnings("unchecked")
    public List<LandmarkSearchResult> findNearPoint(
            double latitude,
            double longitude,
            double radiusMeters,
            String category,
            Integer limit) {
        
        String sql = """
            SELECT 
                id,
                name,
                category::text,
                district,
                ST_Y(location) as latitude,
                ST_X(location) as longitude,
                description,
                popularity_score,
                ST_Distance(
                    location::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                ) as distance_meters
            FROM landmark_locations
            WHERE is_active = true
            AND (:category IS NULL OR category = :category)
            AND ST_DWithin(
                location::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                :radius
            )
            ORDER BY distance_meters ASC, popularity_score DESC
            LIMIT :limit
        """;
        
        List<Object[]> results = getEntityManager()
            .createNativeQuery(sql)
            .setParameter("lat", latitude)
            .setParameter("lon", longitude)
            .setParameter("radius", radiusMeters)
            .setParameter("category", category)
            .setParameter("limit", limit != null ? limit : 10)
            .getResultList();
        
        return results.stream()
            .map(row -> new LandmarkSearchResult(
                (UUID) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                ((Number) row[4]).doubleValue(),
                ((Number) row[5]).doubleValue(),
                (String) row[6],
                ((Number) row[7]).intValue(),
                ((Number) row[8]).floatValue()
            ))
            .toList();
    }
    
    /**
     * Get most popular landmarks
     */
    public List<LandmarkLocation> findMostPopular(Integer limit) {
        return find("isActive = true order by popularityScore desc, name")
            .page(0, limit != null ? limit : 50)
            .list();
    }
    
    /**
     * Increment popularity score
     */
    public void incrementPopularity(UUID landmarkId) {
        getEntityManager()
            .createQuery("UPDATE LandmarkLocation l SET l.popularityScore = l.popularityScore + 1 WHERE l.id = :id")
            .setParameter("id", landmarkId)
            .executeUpdate();
    }
    
    // DTO for search results
    public record LandmarkSearchResult(
        UUID id,
        String name,
        String category,
        String district,
        double latitude,
        double longitude,
        String description,
        int popularityScore,
        float score  // relevance or distance
    ) {}
}