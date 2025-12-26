package com.commute.metrosync.repository;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import com.commute.metrosync.entity.Vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class VehicleRepository implements PanacheRepositoryBase<Vehicle, UUID> {
    
    public List<Vehicle> findByOwner(UUID ownerId) {
        return find("owner.id = ?1 and isActive = true", ownerId)
                .stream()
                .sorted((v1, v2) -> v2.getCreatedAt().compareTo(v1.getCreatedAt()))
                .toList();
    }
    
    public Optional<Vehicle> findByLicensePlate(String licensePlate) {
        return find("licensePlate", licensePlate).firstResultOptional();
    }
    
    public void flush() {
        getEntityManager().flush();
    }
}