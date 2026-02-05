package com.commute.metrosync.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_user", columnList = "user_id"),
    @Index(name = "idx_transactions_booking", columnList = "booking_id"),
    @Index(name = "idx_transactions_reference", columnList = "reference"),
    @Index(name = "idx_transactions_created", columnList = "created_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class Transaction extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(unique = true, length = 100)
    private String reference;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_gateway", length = 50)
    private String paymentGateway;

    @Column(name = "gateway_reference")
    private String gatewayReference;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        CREDIT, DEBIT
    }

    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, REVERSED
    }

    public static java.util.List<Transaction> findByUser(Long userId) {
        return list("user.id = ?1 order by createdAt desc", userId);
    }
}