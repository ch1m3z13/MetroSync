package com.commute.metrosync.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import com.commute.metrosync.entity.Booking;

import java.util.List;

@ApplicationScoped
public class BookingRepository implements PanacheRepository<Booking> {

    public List<Booking> findByRider(Long riderId) {
        return list("rider.id", riderId);
    }

    public List<Booking> findByRide(Long rideId) {
        return list("ride.id", rideId);
    }
}