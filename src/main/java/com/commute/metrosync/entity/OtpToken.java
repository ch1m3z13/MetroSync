package com.commute.metrosync.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_tokens", indexes = {
    @Index(name = "idx_otp_phone", columnList = "phone_number"),
    @Index(name = "idx_otp_expires", columnList = "expires_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class OtpToken extends PanacheEntity {

    @Column(name = "phone_number", nullable = false, length = 15)
    private String phoneNumber;

    @Column(name = "otp_code", nullable = false, length = 6)
    private String otpCode;

    @Column(nullable = false, length = 50)
    private String purpose; // e.g., REGISTRATION, LOGIN, WITHDRAWAL

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column
    private boolean verified = false;

    @Column
    private Integer attempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}