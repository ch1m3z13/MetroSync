package com.commute.metrosync.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallets", indexes = {
    @Index(name = "idx_wallets_user", columnList = "user_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class Wallet extends PanacheEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private Integer balance = 0;

    @Column(name = "total_earned", nullable = false)
    private Integer totalEarned = 0;

    @Column(name = "total_spent", nullable = false)
    private Integer totalSpent = 0;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public synchronized void credit(int amount) {
        this.balance += amount;
        this.totalEarned += amount;
    }

    public synchronized void debit(int amount) {
        if (balance < amount) {
            throw new IllegalStateException("Insufficient funds");
        }
        this.balance -= amount;
        this.totalSpent += amount;
    }

    public boolean hasSufficientFunds(int amount) {
        return balance >= amount;
    }

    public static Wallet findByUser(Long userId) {
        return find("user.id", userId).firstResult();
    }
}