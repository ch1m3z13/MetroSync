package com.commute.metrosync.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import com.commute.metrosync.entity.Route;

import java.util.List;

@ApplicationScoped
public class RouteRepository implements PanacheRepository<Route> {
    
    public List<Route> findActiveRoutesByDriver(Long driverId) {
        return list("driver.id = ?1 and isActive = true", driverId);
    }

    public List<Route> findAllActive() {
        return list("isActive", true);
    }
}