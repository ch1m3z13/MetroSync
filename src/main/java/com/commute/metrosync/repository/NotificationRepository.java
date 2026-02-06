package com.commute.metrosync.repository;

import com.commute.metrosync.entity.Notification;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class NotificationRepository implements PanacheRepositoryBase<Notification, UUID> {

    @Inject
    EntityManager em;

    public List<Notification> findByUserId(UUID userId) {
        return find("user.id = ?1 ORDER BY createdAt DESC", userId).list();
    }

    public List<Notification> findByUserId(UUID userId, int page, int size) {
        return find("user.id = ?1 ORDER BY createdAt DESC", userId)
            .page(Page.of(page, size))
            .list();
    }

    public List<Notification> findRecentByUserId(UUID userId, int limit) {
        return find("user.id = ?1 ORDER BY createdAt DESC", userId)
            .page(0, limit)
            .list();
    }

    public List<Notification> findUnreadByUserId(UUID userId) {
        return find("user.id = ?1 AND isRead = false ORDER BY createdAt DESC", userId)
            .list();
    }

    public List<Notification> findReadByUserId(UUID userId) {
        return find("user.id = ?1 AND isRead = true ORDER BY createdAt DESC", userId)
            .list();
    }

    public long countUnreadByUserId(UUID userId) {
        return count("user.id = ?1 AND isRead = false", userId);
    }

    public List<Notification> findByType(Notification.NotificationType type) {
        return find("type = ?1 ORDER BY createdAt DESC", type).list();
    }

    public List<Notification> findByUserIdAndType(UUID userId, Notification.NotificationType type) {
        return find("user.id = ?1 AND type = ?2 ORDER BY createdAt DESC", userId, type)
            .list();
    }

    public List<Notification> findByPriority(Notification.Priority priority) {
        return find("priority = ?1 ORDER BY createdAt DESC", priority).list();
    }

    public List<Notification> findUnsentPushNotifications() {
        return find("isSent = false AND 'PUSH' = ANY(deliveryChannels)")
            .list();
    }

    public List<Notification> findUnsentPushNotificationsByUserId(UUID userId) {
        return find("user.id = ?1 AND isSent = false AND 'PUSH' = ANY(deliveryChannels)", userId)
            .list();
    }

    public List<Notification> findByBookingId(UUID bookingId) {
        return find("booking.id = ?1 ORDER BY createdAt DESC", bookingId).list();
    }

    public List<Notification> findByTransactionId(UUID transactionId) {
        return find("transaction.id = ?1 ORDER BY createdAt DESC", transactionId).list();
    }

    public List<Notification> findExpired() {
        return find("expiresAt IS NOT NULL AND expiresAt < ?1", LocalDateTime.now())
            .list();
    }

    @Transactional
    public boolean markAsRead(UUID notificationId) {
        return em.createQuery(
            "UPDATE Notification n SET n.isRead = true, n.readAt = :now " +
            "WHERE n.id = :id AND n.isRead = false")
            .setParameter("now", LocalDateTime.now())
            .setParameter("id", notificationId)
            .executeUpdate() > 0;
    }

    @Transactional
    public int markAllAsReadForUser(UUID userId) {
        return em.createQuery(
            "UPDATE Notification n SET n.isRead = true, n.readAt = :now " +
            "WHERE n.user.id = :userId AND n.isRead = false")
            .setParameter("now", LocalDateTime.now())
            .setParameter("userId", userId)
            .executeUpdate();
    }

    @Transactional
    public boolean markAsSent(UUID notificationId) {
        return em.createQuery(
            "UPDATE Notification n SET n.isSent = true, n.sentAt = :now WHERE n.id = :id")
            .setParameter("now", LocalDateTime.now())
            .setParameter("id", notificationId)
            .executeUpdate() > 0;
    }

    @Transactional
    public int markMultipleAsSent(List<UUID> notificationIds) {
        return em.createQuery(
            "UPDATE Notification n SET n.isSent = true, n.sentAt = :now WHERE n.id IN :ids")
            .setParameter("now", LocalDateTime.now())
            .setParameter("ids", notificationIds)
            .executeUpdate();
    }

    @Transactional
    public long deleteExpired() {
        return delete("expiresAt IS NOT NULL AND expiresAt < ?1", LocalDateTime.now());
    }

    @Transactional
    public long deleteOldRead(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        return delete("isRead = true AND createdAt < ?1", cutoffDate);
    }

    public NotificationStatistics getUserStatistics(UUID userId) {
        long totalNotifications = count("user.id", userId);
        long unreadCount = countUnreadByUserId(userId);
        long readCount = totalNotifications - unreadCount;
        
        return new NotificationStatistics(totalNotifications, unreadCount, readCount);
    }

    public NotificationStatistics getGlobalStatistics() {
        long totalNotifications = count();
        long unreadCount = count("isRead", false);
        long readCount = count("isRead", true);
        long unsentPushCount = count("isSent = false AND 'PUSH' = ANY(deliveryChannels)");
        
        return new NotificationStatistics(totalNotifications, unreadCount, readCount, unsentPushCount);
    }

    public static class NotificationStatistics {
        public final long totalNotifications;
        public final long unreadCount;
        public final long readCount;
        public final long unsentPushCount;

        public NotificationStatistics(long totalNotifications, long unreadCount, long readCount) {
            this(totalNotifications, unreadCount, readCount, 0L);
        }

        public NotificationStatistics(
            long totalNotifications,
            long unreadCount,
            long readCount,
            long unsentPushCount
        ) {
            this.totalNotifications = totalNotifications;
            this.unreadCount = unreadCount;
            this.readCount = readCount;
            this.unsentPushCount = unsentPushCount;
        }
    }
}