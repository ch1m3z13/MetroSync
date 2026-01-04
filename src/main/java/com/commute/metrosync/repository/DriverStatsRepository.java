package com.commute.metrosync.repository;

import com.commute.metrosync.entity.DriverStatsView;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class DriverStatsRepository implements PanacheRepositoryBase<DriverStatsView, UUID> {
    
    /**
     * Fetch stats for a specific driver.
     * Since this maps to a View, the ID is the driver's User ID.
     */
    public DriverStatsView findByDriverId(UUID driverId) {
        return findById(driverId);
    }
}