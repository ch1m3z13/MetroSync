package com.commute.metrosync.repository;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import com.commute.metrosync.entity.*;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<User, UUID> {
    
    // ✅ Don't override findById - use Panache's built-in methods:
    // - findById(UUID) -> returns User (nullable)
    // - findByIdOptional(UUID) -> returns Optional<User>
    
    public Optional<User> findByUsername(String username) {
        return find("username", username).firstResultOptional();
    }
    
    public Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
    
    public Optional<User> findByPhoneNumber(String phoneNumber) {
        return find("phoneNumber", phoneNumber).firstResultOptional();
    }
    
    public boolean existsByUsername(String username) {
        return find("username", username).count() > 0;
    }
    
    public boolean existsByEmail(String email) {
        return find("email", email).count() > 0;
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
        
        return getEntityManager()
                .createNativeQuery(sql, User.class)
                .setParameter("lon", location.getX())
                .setParameter("lat", location.getY())
                .setParameter("radius", radiusMeters)
                .getResultList();
    }
    
    // Helper method to flush changes to the database
    public void flush() {
        getEntityManager().flush();
    }
}