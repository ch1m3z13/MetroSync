package com.commute.metrosync.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import com.commute.metrosync.entity.Transaction;

import java.util.List;

@ApplicationScoped
public class TransactionRepository implements PanacheRepository<Transaction> {

    public List<Transaction> findByUser(Long userId) {
        return list("user.id = ?1 ORDER BY createdAt DESC", userId);
    }
}