package com.commute.metrosync.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import com.commute.metrosync.entity.Route;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.dto.request.CreateRouteRequest;
import com.commute.metrosync.exception.BusinessException;
import com.commute.metrosync.repository.RouteRepository;
import com.commute.metrosync.repository.UserRepository;
import com.commute.metrosync.util.GeometryUtil;

import java.util.List;

@ApplicationScoped
public class RouteService {

    @Inject
    RouteRepository routeRepository;

    @Inject
    UserRepository userRepository;

    @Transactional
    public Route createRoute(Long userId, CreateRouteRequest request) {
        User driver = userRepository.findById(userId);
        if (driver == null) {
            throw new BusinessException("Driver not found");
        }

        if (driver.getRole() != User.UserRole.DRIVER) {
            throw new BusinessException("User is not a driver");
        }

        Route route = new Route();
        route.setDriver(driver);
        route.setFromLocation(request.getFromLocation());
        route.setFromPoint(GeometryUtil.createPoint(request.getFromLatitude(), request.getFromLongitude()));
        route.setToLocation(request.getToLocation());
        route.setToPoint(GeometryUtil.createPoint(request.getToLatitude(), request.getToLongitude()));
        route.setDepartureTime(request.getDepartureTime());
        route.setTotalSeats(request.getTotalSeats());
        route.setPricePerSeat(request.getPricePerSeat());
        route.setRecurring(request.getRecurring());
        route.setDaysOfWeek(request.getDaysOfWeek());
        route.setIsActive(true);

        routeRepository.persist(route);
        return route;
    }

    public List<Route> getDriverRoutes(Long driverId) {
        return routeRepository.findActiveRoutesByDriver(driverId);
    }

    public Route getRoute(Long id) {
        return routeRepository.findById(id);
    }
}