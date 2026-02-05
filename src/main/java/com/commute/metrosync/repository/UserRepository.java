package com.commute.metrosync.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import com.commute.metrosync.entity.User;

@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {
    
    public User findByPhoneNumber(String phoneNumber) {
        return find("phoneNumber", phoneNumber).firstResult();
    }
}