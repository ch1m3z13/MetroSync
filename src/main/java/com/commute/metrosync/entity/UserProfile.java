package com.commute.metrosync.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles", indexes = {
    @Index(name = "idx_profiles_nin", columnList = "nin"),
    @Index(name = "idx_profiles_user", columnList = "user_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class UserProfile extends PanacheEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, unique = true, length = 11)
    private String nin;

    @Column(name = "selfie_verified", nullable = false)
    private Boolean selfieVerified = false;

    @Column(name = "selfie_url", length = 500)
    private String selfieUrl;

    @Column(precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "total_trips")
    private Integer totalTrips = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void incrementTrips() {
        this.totalTrips++;
    }

    public void updateRating(BigDecimal newRating) {
        if (this.totalTrips == 0) {
            this.rating = newRating;
        } else {
            this.rating = this.rating
                .multiply(BigDecimal.valueOf(this.totalTrips))
                .add(newRating)
                .divide(BigDecimal.valueOf(this.totalTrips + 1), 2, java.math.RoundingMode.HALF_UP);
        }
    }
}
