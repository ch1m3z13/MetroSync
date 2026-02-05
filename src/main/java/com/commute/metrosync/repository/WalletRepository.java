package com.commute.metrosync.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import com.commute.metrosync.entity.Wallet;

@ApplicationScoped
public class WalletRepository implements PanacheRepository<Wallet> {

    public Wallet findByUser(Long userId) {
        return find("user.id", userId).firstResult();
    }
}
