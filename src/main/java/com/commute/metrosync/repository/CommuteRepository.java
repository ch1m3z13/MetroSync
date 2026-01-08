package com.commute.metrosync.repository;

import com.commute.metrosync.entity.DriverCommute;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CommuteRepository implements PanacheRepositoryBase<DriverCommute, UUID> {
    
    /**
     * Find commute by driver ID
     */
    public Optional<DriverCommute> findByDriverId(UUID driverId) {
        return find("driver.id", driverId).firstResultOptional();
    }
    
    /**
     * Check if driver has a commute set up
     */
    public boolean existsByDriverId(UUID driverId) {
        return count("driver.id", driverId) > 0;
    }
    
    /**
     * Find all active commutes (for analytics/admin)
     */
    public long countActiveCommutes() {
        return count("isActive", true);
    }
    
    public void flush() {
        getEntityManager().flush();
    }
}