package com.commute.metrosync.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import com.commute.metrosync.entity.Route;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.commute.metrosync.entity.Booking;
import com.commute.metrosync.entity.BookingStatus;
import java.time.LocalDateTime;

@ApplicationScoped
public class BookingRepository {
    
    @Inject
    EntityManager em;
    
    public Optional<Booking> findById(UUID id) {
        return Optional.ofNullable(em.find(Booking.class, id));
    }
    
    public Booking save(Booking booking) {
        if (booking.getId() == null) {
            em.persist(booking);
            return booking;
        } else {
            return em.merge(booking);
        }
    }
    
    public void delete(UUID id) {
        findById(id).ifPresent(em::remove);
    }
    
    /**
     * Find all bookings for a rider
     */
    public List<Booking> findByRiderId(UUID riderId) {
        return em.createQuery(
                "SELECT b FROM Booking b WHERE b.rider.id = :riderId " +
                "ORDER BY b.scheduledPickupTime DESC", Booking.class)
                .setParameter("riderId", riderId)
                .getResultList();
    }
    
    /**
     * Find all bookings for a route (for drivers)
     */
    public List<Booking> findByRouteId(UUID routeId) {
        return em.createQuery(
                "SELECT b FROM Booking b WHERE b.route.id = :routeId " +
                "ORDER BY b.scheduledPickupTime ASC", Booking.class)
                .setParameter("routeId", routeId)
                .getResultList();
    }
    
    /**
     * Find active bookings for a route
     */
    public List<Booking> findActiveBookingsByRoute(UUID routeId) {
        return em.createQuery(
                "SELECT b FROM Booking b WHERE b.route.id = :routeId " +
                "AND b.status IN :statuses " +
                "ORDER BY b.scheduledPickupTime ASC", Booking.class)
                .setParameter("routeId", routeId)
                .setParameter("statuses", List.of(
                    BookingStatus.CONFIRMED, 
                    BookingStatus.IN_PROGRESS
                ))
                .getResultList();
    }
    
    /**
     * Find upcoming bookings for a rider
     */
    public List<Booking> findUpcomingBookings(UUID riderId) {
        return em.createQuery(
                "SELECT b FROM Booking b WHERE b.rider.id = :riderId " +
                "AND b.status IN :statuses " +
                "AND b.scheduledPickupTime > :now " +
                "ORDER BY b.scheduledPickupTime ASC", Booking.class)
                .setParameter("riderId", riderId)
                .setParameter("statuses", List.of(
                    BookingStatus.PENDING,
                    BookingStatus.CONFIRMED
                ))
                .setParameter("now", LocalDateTime.now())
                .getResultList();
    }
    
    /**
     * Find bookings by status
     */
    public List<Booking> findByStatus(BookingStatus status) {
        return em.createQuery(
                "SELECT b FROM Booking b WHERE b.status = :status " +
                "ORDER BY b.scheduledPickupTime ASC", Booking.class)
                .setParameter("status", status)
                .getResultList();
    }
    
    /**
     * Find pending bookings for a driver (via routes they own)
     */
    public List<Booking> findPendingBookingsForDriver(UUID driverId) {
        return em.createQuery(
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
        
        return em.createQuery(
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
        return em.createQuery(
                "SELECT b FROM Booking b " +
                "WHERE b.status = :status " +
                "AND b.completedAt BETWEEN :start AND :end " +
                "ORDER BY b.completedAt DESC", Booking.class)
                .setParameter("status", BookingStatus.COMPLETED)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }
}
