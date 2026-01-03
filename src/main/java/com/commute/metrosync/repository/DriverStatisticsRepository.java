package com.commute.metrosync.repository;

import com.commute.metrosync.entity.Booking;
import com.commute.metrosync.entity.BookingStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for driver-specific statistics and metrics queries
 */
@ApplicationScoped
public class DriverStatisticsRepository implements PanacheRepository<Booking> {
    
    /**
     * Count active passengers across multiple routes
     */
    public int countActivePassengers(List<UUID> routeIds) {
        if (routeIds.isEmpty()) return 0;
        
        return getEntityManager()
                .createQuery(
                    "SELECT COALESCE(SUM(b.passengerCount), 0) " +
                    "FROM Booking b " +
                    "WHERE b.route.id IN :routeIds " +
                    "AND b.status = :status", 
                    Long.class)
                .setParameter("routeIds", routeIds)
                .setParameter("status", BookingStatus.IN_PROGRESS)
                .getSingleResult()
                .intValue();
    }
    
    /**
     * Count pending booking requests
     */
    public int countPendingRequests(List<UUID> routeIds) {
        if (routeIds.isEmpty()) return 0;
        
        return getEntityManager()
                .createQuery(
                    "SELECT COUNT(b) " +
                    "FROM Booking b " +
                    "WHERE b.route.id IN :routeIds " +
                    "AND b.status = :status " +
                    "AND b.scheduledPickupTime > :now", 
                    Long.class)
                .setParameter("routeIds", routeIds)
                .setParameter("status", BookingStatus.PENDING)
                .setParameter("now", LocalDateTime.now())
                .getSingleResult()
                .intValue();
    }
    
    /**
     * Find next upcoming booking for route
     */
    public Booking findNextUpcomingBooking(List<UUID> routeIds) {
        if (routeIds.isEmpty()) return null;
        
        List<Booking> results = getEntityManager()
                .createQuery(
                    "SELECT b FROM Booking b " +
                    "WHERE b.route.id IN :routeIds " +
                    "AND b.status IN :statuses " +
                    "AND b.scheduledPickupTime > :now " +
                    "ORDER BY b.scheduledPickupTime ASC", 
                    Booking.class)
                .setParameter("routeIds", routeIds)
                .setParameter("statuses", List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS))
                .setParameter("now", LocalDateTime.now())
                .setMaxResults(1)
                .getResultList();
        
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * Get completed bookings for a date range
     */
    public List<Booking> findCompletedBookings(
            List<UUID> routeIds,
            LocalDateTime startDate,
            LocalDateTime endDate) {
        
        if (routeIds.isEmpty()) return List.of();
        
        return getEntityManager()
                .createQuery(
                    "SELECT b FROM Booking b " +
                    "WHERE b.route.id IN :routeIds " +
                    "AND b.completedAt BETWEEN :start AND :end " +
                    "AND b.status = :status", 
                    Booking.class)
                .setParameter("routeIds", routeIds)
                .setParameter("start", startDate)
                .setParameter("end", endDate)
                .setParameter("status", BookingStatus.COMPLETED)
                .getResultList();
    }
    
    /**
     * Count total booking requests for date range
     */
    public long countTotalRequests(List<UUID> routeIds, LocalDateTime since) {
        if (routeIds.isEmpty()) return 0;
        
        return getEntityManager()
                .createQuery(
                    "SELECT COUNT(b) FROM Booking b " +
                    "WHERE b.route.id IN :routeIds " +
                    "AND b.createdAt >= :since",
                    Long.class)
                .setParameter("routeIds", routeIds)
                .setParameter("since", since)
                .getSingleResult();
    }
    
    /**
     * Count accepted booking requests for date range
     */
    public long countAcceptedRequests(List<UUID> routeIds, LocalDateTime since) {
        if (routeIds.isEmpty()) return 0;
        
        return getEntityManager()
                .createQuery(
                    "SELECT COUNT(b) FROM Booking b " +
                    "WHERE b.route.id IN :routeIds " +
                    "AND b.status IN :statuses " +
                    "AND b.createdAt >= :since",
                    Long.class)
                .setParameter("routeIds", routeIds)
                .setParameter("statuses", List.of(
                    BookingStatus.CONFIRMED,
                    BookingStatus.IN_PROGRESS,
                    BookingStatus.COMPLETED
                ))
                .setParameter("since", since)
                .getSingleResult();
    }
}