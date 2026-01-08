package com.commute.metrosync.repository;

import com.commute.metrosync.entity.CommuteDirection;
import com.commute.metrosync.entity.RouteVariation;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RouteVariationRepository implements PanacheRepositoryBase<RouteVariation, UUID> {
    
    /**
     * Find all route variations for a commute
     */
    public List<RouteVariation> findByCommuteId(UUID commuteId) {
        return find("commute.id = ?1 and isActive = true order by isPreferred desc, name", 
                    commuteId).list();
    }
    
    /**
     * Find route variations by direction
     */
    public List<RouteVariation> findByCommuteIdAndDirection(
            UUID commuteId, 
            CommuteDirection direction) {
        return find("commute.id = ?1 and direction = ?2 and isActive = true order by isPreferred desc", 
                    commuteId, direction).list();
    }
    
    /**
     * Find preferred route for a direction
     */
    public Optional<RouteVariation> findPreferredRoute(
            UUID commuteId, 
            CommuteDirection direction) {
        return find("commute.id = ?1 and direction = ?2 and isPreferred = true and isActive = true",
                    commuteId, direction).firstResultOptional();
    }
    
    /**
     * Set a route as preferred (unsets others)
     */
    public void setPreferred(UUID variationId, UUID commuteId, CommuteDirection direction) {
        // Unset all other preferred routes for this direction
        update("isPreferred = false where commute.id = ?1 and direction = ?2", 
               commuteId, direction);
        
        // Set this one as preferred
        update("isPreferred = true where id = ?1", variationId);
    }
    
    /**
     * Count active variations for a commute
     */
    public long countByCommute(UUID commuteId) {
        return count("commute.id = ?1 and isActive = true", commuteId);
    }
}