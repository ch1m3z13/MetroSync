package com.commute.metrosync.entity;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * EmploymentInfo entity - Work verification and office location
 * Links to User table (one-to-one relationship)
 */
@Entity
@Table(name = "employment_info", indexes = {
    @Index(name = "idx_employment_info_user_id", columnList = "user_id"),
    @Index(name = "idx_employment_info_company", columnList = "company_name"),
    @Index(name = "idx_employment_info_verification_status", columnList = "verification_status")
})
public class EmploymentInfo extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Company Details
    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "company_email", length = 255)
    private String companyEmail;

    @Column(name = "company_phone", length = 20)
    private String companyPhone;

    // Work Location
    @Column(name = "work_address", nullable = false, columnDefinition = "TEXT")
    private String workAddress;

    @Column(name = "work_city", length = 100)
    private String workCity;

    @Column(name = "work_state", length = 50)
    private String workState;

    @Column(name = "work_location", columnDefinition = "geometry(Point,4326)")
    private Point workLocation;  // PostGIS point for office

    // Employment Proof Documents
    @Column(name = "id_card_url", length = 500)
    private String idCardUrl;  // Company ID card photo

    @Column(name = "employment_letter_url", length = 500)
    private String employmentLetterUrl;  // Employment letter/offer letter

    // Employment Details
    @Column(name = "job_title", length = 150)
    private String jobTitle;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "employee_id", length = 50)
    private String employeeId;

    @Column(name = "start_date")
    private LocalDate startDate;

    // Work Schedule
    @Column(name = "work_days", length = 50)
    private String workDays;  // e.g., "MON,TUE,WED,THU,FRI"

    @Column(name = "work_start_time")
    private LocalTime workStartTime;

    @Column(name = "work_end_time")
    private LocalTime workEndTime;

    // Verification Status
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // Enum
    public enum VerificationStatus {
        PENDING, VERIFIED, REJECTED
    }

    // Business Methods
    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }

    public boolean hasIdCard() {
        return idCardUrl != null && !idCardUrl.isEmpty();
    }

    public boolean hasEmploymentLetter() {
        return employmentLetterUrl != null && !employmentLetterUrl.isEmpty();
    }

    public boolean hasAllDocuments() {
        return hasIdCard() && hasEmploymentLetter();
    }

    public String[] getWorkDaysArray() {
        if (workDays == null || workDays.isEmpty()) {
            return new String[0];
        }
        return workDays.split(",");
    }

    public void setWorkDaysArray(String[] days) {
        this.workDays = String.join(",", days);
    }

    public boolean isWorkDay(String day) {
        if (workDays == null) {
            return false;
        }
        return workDays.contains(day.toUpperCase());
    }

    // Getters and Setters
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getCompanyEmail() { return companyEmail; }
    public void setCompanyEmail(String companyEmail) { this.companyEmail = companyEmail; }

    public String getCompanyPhone() { return companyPhone; }
    public void setCompanyPhone(String companyPhone) { this.companyPhone = companyPhone; }

    public String getWorkAddress() { return workAddress; }
    public void setWorkAddress(String workAddress) { this.workAddress = workAddress; }

    public String getWorkCity() { return workCity; }
    public void setWorkCity(String workCity) { this.workCity = workCity; }

    public String getWorkState() { return workState; }
    public void setWorkState(String workState) { this.workState = workState; }

    public Point getWorkLocation() { return workLocation; }
    public void setWorkLocation(Point workLocation) { this.workLocation = workLocation; }

    public String getIdCardUrl() { return idCardUrl; }
    public void setIdCardUrl(String idCardUrl) { this.idCardUrl = idCardUrl; }

    public String getEmploymentLetterUrl() { return employmentLetterUrl; }
    public void setEmploymentLetterUrl(String employmentLetterUrl) { 
        this.employmentLetterUrl = employmentLetterUrl; 
    }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public String getWorkDays() { return workDays; }
    public void setWorkDays(String workDays) { this.workDays = workDays; }

    public LocalTime getWorkStartTime() { return workStartTime; }
    public void setWorkStartTime(LocalTime workStartTime) { 
        this.workStartTime = workStartTime; 
    }

    public LocalTime getWorkEndTime() { return workEndTime; }
    public void setWorkEndTime(LocalTime workEndTime) { this.workEndTime = workEndTime; }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { 
        this.verificationStatus = verificationStatus; 
    }

    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String verificationNotes) { 
        this.verificationNotes = verificationNotes; 
    }

    public User getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(User verifiedBy) { this.verifiedBy = verifiedBy; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
}