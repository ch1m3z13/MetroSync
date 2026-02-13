package com.commute.metrosync.service;

import com.commute.metrosync.dto.*;
import com.commute.metrosync.dto.NotificationDTOs.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Notification Management Service
 * Handles in-app notification queries, statistics, and preferences
 * 
 * NOTE: This is separate from NotificationService which handles sending notifications
 */
@ApplicationScoped
@Transactional
public class NotificationManagementService {
    
    private static final Logger LOG = Logger.getLogger(NotificationManagementService.class);
    
    @PersistenceContext
    EntityManager entityManager;
    
    @Inject
    TermiiService termiiService;
    
    /**
     * Get paginated notifications for a user
     */
    @SuppressWarnings("unchecked")
    public PagedResult<NotificationResponse> getNotifications(
            UUID userId, boolean unreadOnly, String type, String priority, 
            int page, int size) {
        
        LOG.infof("Getting notifications for user %s: unreadOnly=%s", userId, unreadOnly);
        
        StringBuilder queryBuilder = new StringBuilder(
            "SELECT id, user_id, title, message, type, priority, is_read, read_at, " +
            "action_type, booking_id, transaction_id, created_at " +
            "FROM notifications WHERE user_id = :userId "
        );
        
        if (unreadOnly) {
            queryBuilder.append("AND is_read = false ");
        }
        if (type != null) {
            queryBuilder.append("AND type = :type ");
        }
        if (priority != null) {
            queryBuilder.append("AND priority = :priority ");
        }
        
        queryBuilder.append("ORDER BY created_at DESC ");
        queryBuilder.append("LIMIT :limit OFFSET :offset");
        
        var query = entityManager.createNativeQuery(queryBuilder.toString())
            .setParameter("userId", userId)
            .setParameter("limit", size)
            .setParameter("offset", page * size);
        
        if (type != null) {
            query.setParameter("type", type);
        }
        if (priority != null) {
            query.setParameter("priority", priority);
        }
        
        List<Object[]> results = query.getResultList();
        
        // Get total count
        String countQuery = "SELECT COUNT(*) FROM notifications WHERE user_id = :userId";
        if (unreadOnly) {
            countQuery += " AND is_read = false";
        }
        
        Long totalCount = ((Number) entityManager.createNativeQuery(countQuery)
            .setParameter("userId", userId)
            .getSingleResult()).longValue();
        
        List<NotificationResponse> notifications = new ArrayList<>();
        for (Object[] row : results) {
            notifications.add(mapToNotificationResponse(row));
        }
        
        return PagedResult.of(notifications, page, size, totalCount);
    }
    
    /**
     * Get a single notification
     */
    public NotificationResponse getNotification(UUID userId, UUID notificationId) {
        String query = "SELECT id, user_id, title, message, type, priority, is_read, read_at, " +
                      "action_type, booking_id, transaction_id, created_at " +
                      "FROM notifications WHERE id = :notificationId AND user_id = :userId";
        
        Object[] result = (Object[]) entityManager.createNativeQuery(query)
            .setParameter("notificationId", notificationId)
            .setParameter("userId", userId)
            .getSingleResult();
        
        if (result == null) {
            throw new IllegalArgumentException("Notification not found");
        }
        
        return mapToNotificationResponse(result);
    }
    
    /**
     * Get unread notification count
     */
    public int getUnreadCount(UUID userId) {
        String query = "SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND is_read = false";
        
        Number result = (Number) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .getSingleResult();
        
        return result != null ? result.intValue() : 0;
    }
    
    /**
     * Mark notification as read
     */
    public MarkReadResponse markAsRead(UUID userId, UUID notificationId) {
        LOG.infof("Marking notification as read: %s", notificationId);
        
        String query = "UPDATE notifications SET is_read = true, read_at = CURRENT_TIMESTAMP, " +
                      "updated_at = CURRENT_TIMESTAMP " +
                      "WHERE id = :notificationId AND user_id = :userId";
        
        int updated = entityManager.createNativeQuery(query)
            .setParameter("notificationId", notificationId)
            .setParameter("userId", userId)
            .executeUpdate();
        
        if (updated == 0) {
            throw new IllegalArgumentException("Notification not found");
        }
        
        MarkReadResponse response = new MarkReadResponse();
        response.setNotificationId(notificationId);
        response.setIsRead(true);
        response.setReadAt(LocalDateTime.now());
        
        return response;
    }
    
    /**
     * Mark all notifications as read
     */
    public int markAllAsRead(UUID userId) {
        LOG.infof("Marking all notifications as read for user: %s", userId);
        
        String query = "UPDATE notifications SET is_read = true, read_at = CURRENT_TIMESTAMP, " +
                      "updated_at = CURRENT_TIMESTAMP " +
                      "WHERE user_id = :userId AND is_read = false";
        
        return entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .executeUpdate();
    }
    
    /**
     * Mark batch of notifications as read
     */
    public int markBatchAsRead(UUID userId, List<UUID> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return 0;
        }
        
        String query = "UPDATE notifications SET is_read = true, read_at = CURRENT_TIMESTAMP, " +
                      "updated_at = CURRENT_TIMESTAMP " +
                      "WHERE user_id = :userId AND id IN :notificationIds";
        
        return entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .setParameter("notificationIds", notificationIds)
            .executeUpdate();
    }
    
    /**
     * Delete notification
     */
    public void deleteNotification(UUID userId, UUID notificationId) {
        String query = "DELETE FROM notifications WHERE id = :notificationId AND user_id = :userId";
        
        int deleted = entityManager.createNativeQuery(query)
            .setParameter("notificationId", notificationId)
            .setParameter("userId", userId)
            .executeUpdate();
        
        if (deleted == 0) {
            throw new IllegalArgumentException("Notification not found");
        }
    }
    
    /**
     * Delete all read notifications
     */
    public int deleteAllRead(UUID userId) {
        String query = "DELETE FROM notifications WHERE user_id = :userId AND is_read = true";
        
        return entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .executeUpdate();
    }
    
    /**
     * Get notification preferences
     */
    public NotificationPreferencesResponse getPreferences(UUID userId) {
        // TODO: Implement preferences table and logic
        NotificationPreferencesResponse response = new NotificationPreferencesResponse();
        response.setEmailNotifications(true);
        response.setSmsNotifications(true);
        response.setPushNotifications(true);
        response.setBookingNotifications(true);
        response.setPaymentNotifications(true);
        response.setPromotionalNotifications(false);
        return response;
    }
    
    /**
     * Update notification preferences
     */
    public NotificationPreferencesResponse updatePreferences(UUID userId, NotificationPreferencesRequest request) {
        // TODO: Implement preferences update
        return getPreferences(userId);
    }
    
    /**
     * Get notification statistics
     */
    public NotificationStatisticsResponse getStatistics(UUID userId) {
        String query = "SELECT COUNT(*) as total, " +
                      "COUNT(*) FILTER (WHERE is_read = false) as unread, " +
                      "COUNT(*) FILTER (WHERE is_read = true) as read, " +
                      "COUNT(*) FILTER (WHERE priority = 'URGENT') as urgent, " +
                      "COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE) as today, " +
                      "COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE - INTERVAL '7 days') as week " +
                      "FROM notifications WHERE user_id = :userId";
        
        Object[] result = (Object[]) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .getSingleResult();
        
        NotificationStatisticsResponse response = new NotificationStatisticsResponse();
        response.setTotalNotifications(((Number) result[0]).intValue());
        response.setUnreadCount(((Number) result[1]).intValue());
        response.setReadCount(((Number) result[2]).intValue());
        response.setUrgentCount(((Number) result[3]).intValue());
        response.setTodayCount(((Number) result[4]).intValue());
        response.setWeekCount(((Number) result[5]).intValue());
        
        return response;
    }
    
    /**
     * Send notification (creates in-app notification)
     */
    public NotificationResponse sendNotification(
            UUID userId, String title, String message, String type, 
            String priority, Map<String, Object> data, String actionUrl) {
        
        LOG.infof("Sending notification to user %s: %s", userId, type);
        
        UUID notificationId = createNotification(
            userId, title, message, type, priority, data, actionUrl, null, null
        );
        
        // Send SMS for high priority notifications
        if ("HIGH".equals(priority) || "URGENT".equals(priority)) {
            sendSmsNotification(userId, message);
        }
        
        NotificationResponse response = new NotificationResponse();
        response.setId(notificationId);
        response.setTitle(title);
        response.setMessage(message);
        response.setType(type);
        response.setPriority(priority);
        response.setData(data);
        response.setActionUrl(actionUrl);
        response.setIsRead(false);
        response.setCreatedAt(LocalDateTime.now());
        
        return response;
    }
    
    /**
     * Broadcast notification to multiple users
     */
    public int broadcastNotification(
            List<UUID> userIds, String title, String message, 
            String type, String priority, Map<String, Object> data) {
        
        LOG.infof("Broadcasting notification to %d users", userIds.size());
        
        int sentCount = 0;
        for (UUID userId : userIds) {
            try {
                sendNotification(userId, title, message, type, priority, data, null);
                sentCount++;
            } catch (Exception e) {
                LOG.warnf("Failed to send notification to user %s: %s", userId, e.getMessage());
            }
        }
        
        return sentCount;
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    private UUID createNotification(
            UUID userId, String title, String message, String type, String priority,
            Map<String, Object> data, String actionUrl, UUID bookingId, UUID routeId) {
        
        String query = "INSERT INTO notifications " +
                      "(user_id, title, message, type, priority, action_type, metadata, created_at) " +
                      "VALUES (:userId, :title, :message, :type, :priority, :actionUrl, " +
                      "CAST(:data AS jsonb), CURRENT_TIMESTAMP) " +
                      "RETURNING id";
        
        UUID notificationId = (UUID) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .setParameter("title", title)
            .setParameter("message", message)
            .setParameter("type", type)
            .setParameter("priority", priority != null ? priority : "NORMAL")
            .setParameter("actionUrl", actionUrl)
            .setParameter("data", data != null ? convertToJson(data) : "{}")
            .getSingleResult();
        
        return notificationId;
    }
    
    private void sendSmsNotification(UUID userId, String message) {
        try {
            // Get user's phone number
            String phoneQuery = "SELECT phone_number FROM users WHERE id = :userId";
            String phoneNumber = (String) entityManager.createNativeQuery(phoneQuery)
                .setParameter("userId", userId)
                .getSingleResult();
            
            if (phoneNumber != null) {
                termiiService.sendSms(phoneNumber, message, "NOTIFICATION");
            }
        } catch (Exception e) {
            LOG.warnf("Failed to send SMS notification: %s", e.getMessage());
        }
    }
    
    private NotificationResponse mapToNotificationResponse(Object[] row) {
        NotificationResponse response = new NotificationResponse();
        
        // Indices: id(0), user_id(1), title(2), message(3), type(4), priority(5),
        //          is_read(6), read_at(7), action_type(8), booking_id(9), 
        //          transaction_id(10), created_at(11)
        
        response.setId((UUID) row[0]);
        response.setTitle((String) row[2]);
        response.setMessage((String) row[3]);
        response.setType((String) row[4]);
        response.setPriority((String) row[5]);
        response.setIsRead((Boolean) row[6]);
        response.setReadAt((LocalDateTime) row[7]);
        response.setActionUrl((String) row[8]);
        
        if (row[9] != null) {
            response.setBookingId((UUID) row[9]);
        }
        if (row[10] != null) {
            response.setTransactionId((UUID) row[10]);
        }
        
        response.setCreatedAt((LocalDateTime) row[11]);
        
        return response;
    }
    
    private String convertToJson(Map<String, Object> data) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
}