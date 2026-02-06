package com.commute.metrosync.repository;

import com.commute.metrosync.entity.Transaction;
import com.commute.metrosync.entity.Wallet;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TransactionRepository implements PanacheRepositoryBase<Transaction, UUID> {

    @Inject
    EntityManager em;

    public Optional<Transaction> findByReference(String reference) {
        return find("reference", reference).firstResultOptional();
    }

    public Optional<Transaction> findByExternalReference(String externalReference) {
        return find("externalReference", externalReference).firstResultOptional();
    }

    public List<Transaction> findByUserId(UUID userId) {
        return find("user.id = ?1 ORDER BY createdAt DESC", userId).list();
    }

    public List<Transaction> findByUserId(UUID userId, int page, int size) {
        return find("user.id = ?1 ORDER BY createdAt DESC", userId)
            .page(Page.of(page, size))
            .list();
    }

    public List<Transaction> findRecentByUserId(UUID userId, int limit) {
        return find("user.id = ?1 ORDER BY createdAt DESC", userId)
            .page(0, limit)
            .list();
    }

    public List<Transaction> findByUserIdAndType(UUID userId, Transaction.TransactionType type) {
        return find("user.id = ?1 AND type = ?2 ORDER BY createdAt DESC", userId, type)
            .list();
    }

    public List<Transaction> findByUserIdAndStatus(UUID userId, Transaction.TransactionStatus status) {
        return find("user.id = ?1 AND status = ?2 ORDER BY createdAt DESC", userId, status)
            .list();
    }

    public List<Transaction> findPending() {
        return find("status = ?1 ORDER BY createdAt DESC", Transaction.TransactionStatus.PENDING)
            .list();
    }

    public List<Transaction> findCompleted() {
        return find("status = ?1 ORDER BY createdAt DESC", Transaction.TransactionStatus.COMPLETED)
            .list();
    }

    public List<Transaction> findFailed() {
        return find("status = ?1 ORDER BY createdAt DESC", Transaction.TransactionStatus.FAILED)
            .list();
    }

    public List<Transaction> findByType(Transaction.TransactionType type) {
        return find("type = ?1 ORDER BY createdAt DESC", type).list();
    }

    public List<Transaction> findByBookingId(UUID bookingId) {
        return find("booking.id = ?1 ORDER BY createdAt DESC", bookingId).list();
    }

    public List<Transaction> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return find("createdAt BETWEEN ?1 AND ?2 ORDER BY createdAt DESC", startDate, endDate)
            .list();
    }

    public List<Transaction> findByUserIdAndDateRange(
        UUID userId,
        LocalDateTime startDate,
        LocalDateTime endDate
    ) {
        return find(
            "user.id = ?1 AND createdAt BETWEEN ?2 AND ?3 ORDER BY createdAt DESC",
            userId, startDate, endDate
        ).list();
    }

    public long countByStatus(Transaction.TransactionStatus status) {
        return count("status", status);
    }

    public long countByUserIdAndType(UUID userId, Transaction.TransactionType type) {
        return count("user.id = ?1 AND type = ?2", userId, type);
    }

    /**
     * Get user's total earnings (for drivers)
     */
    public BigDecimal getUserTotalEarnings(UUID userId) {
        Long totalKobo = em.createQuery(
            "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.type = :type AND t.status = :status",
            Long.class)
            .setParameter("userId", userId)
            .setParameter("type", Transaction.TransactionType.RIDE_EARNING)
            .setParameter("status", Transaction.TransactionStatus.COMPLETED)
            .getSingleResult();
        
        return Wallet.toNaira(totalKobo);
    }

    /**
     * Get user's total spending (for riders)
     */
    public BigDecimal getUserTotalSpending(UUID userId) {
        Long totalKobo = em.createQuery(
            "SELECT COALESCE(SUM(ABS(t.amount)), 0) FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.type = :type AND t.status = :status",
            Long.class)
            .setParameter("userId", userId)
            .setParameter("type", Transaction.TransactionType.RIDE_PAYMENT)
            .setParameter("status", Transaction.TransactionStatus.COMPLETED)
            .getSingleResult();
        
        return Wallet.toNaira(totalKobo);
    }

    /**
     * Get total transaction volume by type
     */
    public BigDecimal getTotalVolumeByType(Transaction.TransactionType type) {
        Long totalKobo = em.createQuery(
            "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.type = :type AND t.status = :status",
            Long.class)
            .setParameter("type", type)
            .setParameter("status", Transaction.TransactionStatus.COMPLETED)
            .getSingleResult();
        
        return Wallet.toNaira(totalKobo);
    }

    /**
     * Get total transaction volume for date range
     */
    public BigDecimal getTotalVolumeByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        Long totalKobo = em.createQuery(
            "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.createdAt BETWEEN :start AND :end AND t.status = :status",
            Long.class)
            .setParameter("start", startDate)
            .setParameter("end", endDate)
            .setParameter("status", Transaction.TransactionStatus.COMPLETED)
            .getSingleResult();
        
        return Wallet.toNaira(totalKobo);
    }

    public TransactionStatistics getStatistics() {
        long totalTransactions = count();
        long pendingCount = countByStatus(Transaction.TransactionStatus.PENDING);
        long completedCount = countByStatus(Transaction.TransactionStatus.COMPLETED);
        long failedCount = countByStatus(Transaction.TransactionStatus.FAILED);
        
        BigDecimal totalVolume = getTotalVolumeByType(Transaction.TransactionType.TOP_UP);
        BigDecimal ridePayments = getTotalVolumeByType(Transaction.TransactionType.RIDE_PAYMENT);
        BigDecimal rideEarnings = getTotalVolumeByType(Transaction.TransactionType.RIDE_EARNING);
        
        return new TransactionStatistics(
            totalTransactions,
            pendingCount,
            completedCount,
            failedCount,
            totalVolume,
            ridePayments,
            rideEarnings
        );
    }

    public static class TransactionStatistics {
        public final long totalTransactions;
        public final long pendingCount;
        public final long completedCount;
        public final long failedCount;
        public final BigDecimal totalVolume;
        public final BigDecimal ridePayments;
        public final BigDecimal rideEarnings;

        public TransactionStatistics(
            long totalTransactions,
            long pendingCount,
            long completedCount,
            long failedCount,
            BigDecimal totalVolume,
            BigDecimal ridePayments,
            BigDecimal rideEarnings
        ) {
            this.totalTransactions = totalTransactions;
            this.pendingCount = pendingCount;
            this.completedCount = completedCount;
            this.failedCount = failedCount;
            this.totalVolume = totalVolume;
            this.ridePayments = ridePayments;
            this.rideEarnings = rideEarnings;
        }
    }
}