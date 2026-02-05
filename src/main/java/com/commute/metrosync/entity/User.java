package com.commute.metrosync.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_phone", columnList = "phone_number"),
    @Index(name = "idx_users_status", columnList = "status")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends PanacheEntity {

    @Column(name = "phone_number", unique = true, nullable = false, length = 15)
    private String phoneNumber;

    @Column(name = "phone_verified")
    private boolean phoneVerified = false;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status = UserStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isFullyVerified() {
        // Logic to determine if user is fully verified
        // For now, checks phone verification, but can be expanded to check UserProfile/Employment
        return phoneVerified;
    }

    public enum UserRole {
        RIDER, DRIVER
    }

    public enum UserStatus {
        PENDING, ACTIVE, SUSPENDED, BANNED
    }
}