package com.commute.metrosync.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import com.commute.metrosync.entity.Booking;
import com.commute.metrosync.entity.Ride;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.entity.Wallet;
import com.commute.metrosync.dto.request.CreateBookingRequest;
import com.commute.metrosync.exception.BusinessException;
import com.commute.metrosync.repository.BookingRepository;
import com.commute.metrosync.repository.UserRepository;
import com.commute.metrosync.repository.WalletRepository;
import com.commute.metrosync.util.GeometryUtil;
import com.commute.metrosync.util.PinGenerator;

@ApplicationScoped
public class BookingService {

    @Inject
    BookingRepository bookingRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    WalletRepository walletRepository;

    @Transactional
    public Booking createBooking(Long riderId, CreateBookingRequest request) {
        User rider = userRepository.findById(riderId);
        if (rider == null) throw new BusinessException("Rider not found");

        Ride ride = Ride.findById(request.getRideId());
        if (ride == null) throw new BusinessException("Ride not found");

        if (ride.getAvailableSeats() < request.getSeatsRequested()) {
            throw new BusinessException("Not enough seats available");
        }

        int totalPrice = ride.getRoute().getPricePerSeat() * request.getSeatsRequested();
        Wallet wallet = walletRepository.findByUser(riderId);
        
        if (wallet == null || !wallet.hasSufficientFunds(totalPrice)) {
            throw new BusinessException("Insufficient wallet balance");
        }

        // Deduct funds
        wallet.debit(totalPrice);

        // Create booking
        Booking booking = new Booking();
        booking.setRide(ride);
        booking.setRider(rider);
        booking.setPickupLocation(request.getPickupLocation());
        booking.setPickupPoint(GeometryUtil.createPoint(request.getPickupLatitude(), request.getPickupLongitude()));
        booking.setDropoffLocation(request.getDropoffLocation());
        booking.setDropoffPoint(GeometryUtil.createPoint(request.getDropoffLatitude(), request.getDropoffLongitude()));
        booking.setSeatsRequested(request.getSeatsRequested());
        booking.setPrice(totalPrice);
        booking.setSafetyPin(PinGenerator.generateSafetyPin());
        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        booking.confirm();

        // Update ride seats
        ride.setAvailableSeats(ride.getAvailableSeats() - request.getSeatsRequested());
        
        bookingRepository.persist(booking);
        return booking;
    }
}