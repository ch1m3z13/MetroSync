package com.commute.metrosync.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "employment_info", indexes = {
    @Index(name = "idx_employment_user", columnList = "user_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class EmploymentInfo extends PanacheEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(name = "work_address", nullable = false, columnDefinition = "TEXT")
    private String workAddress;

    @Column(name = "work_location", columnDefinition = "geography(Point, 4326)")
    private Point workLocation;

    @Column(name = "work_start_time", nullable = false)
    private LocalTime workStartTime;

    @Column(name = "work_end_time", nullable = false)
    private LocalTime workEndTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 20)
    private VerificationType verificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void approve() {
        this.verificationStatus = VerificationStatus.VERIFIED;
        this.verifiedAt = LocalDateTime.now();
    }

    public void reject() {
        this.verificationStatus = VerificationStatus.REJECTED;
    }

    public enum VerificationType {
        ID_CARD, EMAIL, LETTER
    }
}