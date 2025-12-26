package com.commute.metrosync.repository;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import com.commute.metrosync.entity.Booking;
import com.commute.metrosync.entity.BookingStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class BookingRepository implements PanacheRepositoryBase<Booking, UUID> {
    
    /**
     * Find booking by reference number (for seeder idempotency)
     */
    public Optional<Booking> findByReferenceNumber(String referenceNumber) {
        return find("referenceNumber", referenceNumber).firstResultOptional();
    }
    
    /**
     * Find all bookings for a rider
     */
    public List<Booking> findByRiderId(UUID riderId) {
        return find("rider.id = ?1 order by scheduledPickupTime desc", riderId).list();
    }
    
    /**
     * Find all bookings for a route (for drivers)
     */
    public List<Booking> findByRouteId(UUID routeId) {
        return find("route.id = ?1 order by scheduledPickupTime asc", routeId).list();
    }
    
    /**
     * Find active bookings for a route
     */
    public List<Booking> findActiveBookingsByRoute(UUID routeId) {
        return find("route.id = ?1 and status in ?2 order by scheduledPickupTime asc", 
                routeId, 
                List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS))
                .list();
    }
    
    /**
     * Find upcoming bookings for a rider
     */
    public List<Booking> findUpcomingBookings(UUID riderId) {
        return find("rider.id = ?1 and status in ?2 and scheduledPickupTime > ?3 order by scheduledPickupTime asc",
                riderId,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED),
                LocalDateTime.now())
                .list();
    }
    
    /**
     * Find bookings by status
     */
    public List<Booking> findByStatus(BookingStatus status) {
        return find("status = ?1 order by scheduledPickupTime asc", status).list();
    }
    
    /**
     * Find pending bookings for a driver (via routes they own)
     */
    public List<Booking> findPendingBookingsForDriver(UUID driverId) {
        return getEntityManager().createQuery(
                "SELECT b FROM Booking b JOIN b.route r " +
                "WHERE r.driverId = :driverId " +
                "AND b.status = :status " +
                "ORDER BY b.scheduledPickupTime ASC", Booking.class)
                .setParameter("driverId", driverId)
                .setParameter("status", BookingStatus.PENDING)
                .getResultList();
    }
    
    /**
     * Count active bookings for a route on a specific date
     */
    public Long countActiveBookingsForRouteOnDate(UUID routeId, LocalDateTime date) {
        LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        
        return getEntityManager().createQuery(
                "SELECT COUNT(b) FROM Booking b " +
                "WHERE b.route.id = :routeId " +
                "AND b.status IN :statuses " +
                "AND b.scheduledPickupTime BETWEEN :start AND :end", Long.class)
                .setParameter("routeId", routeId)
                .setParameter("statuses", List.of(
                    BookingStatus.CONFIRMED,
                    BookingStatus.IN_PROGRESS
                ))
                .setParameter("start", startOfDay)
                .setParameter("end", endOfDay)
                .getSingleResult();
    }
    
    /**
     * Find bookings completed in a date range (for analytics)
     */
    public List<Booking> findCompletedBookingsInRange(
            LocalDateTime start, LocalDateTime end) {
        return find("status = ?1 and completedAt between ?2 and ?3 order by completedAt desc",
                BookingStatus.COMPLETED,
                start,
                end)
                .list();
    }
    
    public void flush() {
        getEntityManager().flush();
    }
}
