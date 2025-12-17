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
public class UserRepository {
    
    @Inject
    EntityManager em;
    
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(em.find(User.class, id));
    }
    
    public Optional<User> findByUsername(String username) {
        return em.createQuery(
                "SELECT u FROM User u WHERE u.username = :username", User.class)
                .setParameter("username", username)
                .getResultStream()
                .findFirst();
    }
    
    public Optional<User> findByEmail(String email) {
        return em.createQuery(
                "SELECT u FROM User u WHERE u.email = :email", User.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst();
    }
    
    public Optional<User> findByPhoneNumber(String phoneNumber) {
        return em.createQuery(
                "SELECT u FROM User u WHERE u.phoneNumber = :phone", User.class)
                .setParameter("phone", phoneNumber)
                .getResultStream()
                .findFirst();
    }
    
    public User save(User user) {
        if (user.getId() == null) {
            em.persist(user);
            return user;
        } else {
            return em.merge(user);
        }
    }
    
    public void delete(UUID id) {
        findById(id).ifPresent(em::remove);
    }
    
    public List<User> findDriversNearLocation(Point location, double radiusMeters) {
        String sql = """
            SELECT u.* FROM users u
            WHERE u.roles LIKE '%DRIVER%'
            AND u.is_active = true
            AND u.current_location IS NOT NULL
            AND ST_DWithin(
                u.current_location::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                :radius
            )
            ORDER BY ST_Distance(
                u.current_location::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
            )
            """;
        
        return em.createNativeQuery(sql, User.class)
                .setParameter("lon", location.getX())
                .setParameter("lat", location.getY())
                .setParameter("radius", radiusMeters)
                .getResultList();
    }
    
    public boolean existsByUsername(String username) {
        Long count = em.createQuery(
                "SELECT COUNT(u) FROM User u WHERE u.username = :username", Long.class)
                .setParameter("username", username)
                .getSingleResult();
        return count > 0;
    }
    
    public boolean existsByEmail(String email) {
        Long count = em.createQuery(
                "SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                .setParameter("email", email)
                .getSingleResult();
        return count > 0;
    }
}