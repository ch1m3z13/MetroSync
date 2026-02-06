package com.commute.metrosync.repository;

import com.commute.metrosync.entity.Wallet;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WalletRepository implements PanacheRepositoryBase<Wallet, UUID> {

    @Inject
    EntityManager em;

    public Optional<Wallet> findByUserId(UUID userId) {
        return find("user.id", userId).firstResultOptional();
    }

    @Transactional
    public Optional<Wallet> findByUserIdForUpdate(UUID userId) {
        List<Wallet> results = em.createQuery(
            "SELECT w FROM Wallet w WHERE w.user.id = :userId", Wallet.class)
            .setParameter("userId", userId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Wallet> findActive() {
        return find("status", Wallet.WalletStatus.ACTIVE).list();
    }

    public List<Wallet> findFrozen() {
        return find("status", Wallet.WalletStatus.FROZEN).list();
    }

    public List<Wallet> findSuspended() {
        return find("status", Wallet.WalletStatus.SUSPENDED).list();
    }

    public List<Wallet> findByStatus(Wallet.WalletStatus status) {
        return find("status", status).list();
    }

    public long countByStatus(Wallet.WalletStatus status) {
        return count("status", status);
    }

    public List<Wallet> findWithBalanceGreaterThan(Long amountInKobo) {
        return find("balance > ?1", amountInKobo).list();
    }

    public List<Wallet> findWithLowBalance(Long thresholdInKobo) {
        return find("balance < ?1", thresholdInKobo).list();
    }

    /**
     * Get total system balance (all active wallets) in Naira
     */
    public BigDecimal getTotalSystemBalance() {
        Long totalKobo = em.createQuery(
            "SELECT COALESCE(SUM(w.balance), 0) FROM Wallet w WHERE w.status = :status", Long.class)
            .setParameter("status", Wallet.WalletStatus.ACTIVE)
            .getSingleResult();
        return Wallet.toNaira(totalKobo);
    }

    @Transactional
    public int resetDailyWithdrawalLimits() {
        return em.createQuery(
            "UPDATE Wallet w SET w.dailyWithdrawalUsed = 0, w.dailyWithdrawalResetDate = :today " +
            "WHERE w.dailyWithdrawalResetDate < :today")
            .setParameter("today", LocalDate.now())
            .executeUpdate();
    }

    public List<Wallet> findWalletsNeedingLimitReset() {
        return find("dailyWithdrawalResetDate < ?1", LocalDate.now()).list();
    }

    @Transactional
    public void credit(UUID walletId, Long amountInKobo) {
        Wallet wallet = findByIdOptional(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
        wallet.credit(amountInKobo);
        persist(wallet);
    }

    @Transactional
    public void debit(UUID walletId, Long amountInKobo) {
        Wallet wallet = findByIdOptional(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
        wallet.debit(amountInKobo);
        persist(wallet);
    }

    @Transactional
    public void freeze(UUID walletId) {
        em.createQuery("UPDATE Wallet w SET w.status = :status WHERE w.id = :id")
            .setParameter("status", Wallet.WalletStatus.FROZEN)
            .setParameter("id", walletId)
            .executeUpdate();
    }

    @Transactional
    public void unfreeze(UUID walletId) {
        em.createQuery("UPDATE Wallet w SET w.status = :status WHERE w.id = :id")
            .setParameter("status", Wallet.WalletStatus.ACTIVE)
            .setParameter("id", walletId)
            .executeUpdate();
    }

    @Transactional
    public void suspend(UUID walletId) {
        em.createQuery("UPDATE Wallet w SET w.status = :status WHERE w.id = :id")
            .setParameter("status", Wallet.WalletStatus.SUSPENDED)
            .setParameter("id", walletId)
            .executeUpdate();
    }

    public WalletStatistics getStatistics() {
        long totalWallets = count();
        long activeWallets = countByStatus(Wallet.WalletStatus.ACTIVE);
        long frozenWallets = countByStatus(Wallet.WalletStatus.FROZEN);
        long suspendedWallets = countByStatus(Wallet.WalletStatus.SUSPENDED);
        BigDecimal totalBalance = getTotalSystemBalance();

        return new WalletStatistics(
            totalWallets,
            activeWallets,
            frozenWallets,
            suspendedWallets,
            totalBalance
        );
    }

    public static class WalletStatistics {
        public final long totalWallets;
        public final long activeWallets;
        public final long frozenWallets;
        public final long suspendedWallets;
        public final BigDecimal totalBalance;

        public WalletStatistics(
            long totalWallets,
            long activeWallets,
            long frozenWallets,
            long suspendedWallets,
            BigDecimal totalBalance
        ) {
            this.totalWallets = totalWallets;
            this.activeWallets = activeWallets;
            this.frozenWallets = frozenWallets;
            this.suspendedWallets = suspendedWallets;
            this.totalBalance = totalBalance;
        }
    }
}