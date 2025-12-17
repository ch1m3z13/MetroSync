package com.commute.metrosync.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import com.commute.metrosync.entity.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class VehicleRepository {
    
    @Inject
    EntityManager em;
    
    public Optional<Vehicle> findById(UUID id) {
        return Optional.ofNullable(em.find(Vehicle.class, id));
    }
    
    public List<Vehicle> findByOwner(UUID ownerId) {
        return em.createQuery(
                "SELECT v FROM Vehicle v WHERE v.owner.id = :ownerId " +
                "AND v.isActive = true ORDER BY v.createdAt DESC", Vehicle.class)
                .setParameter("ownerId", ownerId)
                .getResultList();
    }
    
    public Optional<Vehicle> findByLicensePlate(String licensePlate) {
        return em.createQuery(
                "SELECT v FROM Vehicle v WHERE v.licensePlate = :plate", Vehicle.class)
                .setParameter("plate", licensePlate)
                .getResultStream()
                .findFirst();
    }
    
    public Vehicle save(Vehicle vehicle) {
        if (vehicle.getId() == null) {
            em.persist(vehicle);
            return vehicle;
        } else {
            return em.merge(vehicle);
        }
    }
    
    public void delete(UUID id) {
        findById(id).ifPresent(em::remove);
    }
}
