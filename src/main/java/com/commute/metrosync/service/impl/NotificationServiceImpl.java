package com.commute.metrosync.service.impl;

import com.commute.metrosync.dto.*;
import com.commute.metrosync.service.NotificationService;
import com.commute.metrosync.service.TermiiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

/**
 * Notification Service Implementation
 * Handles in-app notifications and SMS alerts
 */
@ApplicationScoped
@Transactional
public class NotificationServiceImpl implements NotificationService {
    
    private static final Logger logger = Logger.getLogger(NotificationServiceImpl.class.getName());
    
    @PersistenceContext(unitName = "commuteng-pu")
    private EntityManager entityManager;
    
    @Inject
    private TermiiService termiiService;
    
    @Override
    public PagedResult<NotificationResponse> getNotifications(
            UUID userId, boolean unreadOnly, String type, String priority, 
            int page, int size) {
        
        logger.info("Getting notifications for user " + userId + ": unreadOnly=" + unreadOnly);
        
        StringBuilder queryBuilder = new StringBuilder(
            "SELECT * FROM notifications WHERE user_id = :userId "
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
    
    @Override
    public NotificationResponse getNotification(UUID userId, UUID notificationId) {
        String query = "SELECT * FROM notifications WHERE id = :notificationId AND user_id = :userId";
        
        Object[] result = (Object[]) entityManager.createNativeQuery(query)
            .setParameter("notificationId", notificationId)
            .setParameter("userId", userId)
            .getSingleResult();
        
        if (result == null) {
            throw new IllegalArgumentException("Notification not found");
        }
        
        return mapToNotificationResponse(result);
    }
    
    @Override
    public int getUnreadCount(UUID userId) {
        String query = "SELECT get_unread_notification_count(:userId)";
        
        Number result = (Number) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .getSingleResult();
        
        return result != null ? result.intValue() : 0;
    }
    
    @Override
    public MarkReadResponse markAsRead(UUID userId, UUID notificationId) {
        logger.info("Marking notification as read: " + notificationId);
        
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
    
    @Override
    public int markAllAsRead(UUID userId) {
        logger.info("Marking all notifications as read for user: " + userId);
        
        String query = "SELECT mark_all_notifications_read(:userId)";
        
        Number result = (Number) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .getSingleResult();
        
        return result != null ? result.intValue() : 0;
    }
    
    @Override
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
    
    @Override
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
    
    @Override
    public int deleteAllRead(UUID userId) {
        String query = "DELETE FROM notifications WHERE user_id = :userId AND is_read = true";
        
        return entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .executeUpdate();
    }
    
    @Override
    public NotificationPreferencesResponse getPreferences(UUID userId) {
        // TODO: Implement preferences table and logic
        NotificationPreferencesResponse response = new NotificationPreferencesResponse();
        response.setEmailNotifications(true);
        response.setSmsNotifications(true);
        response.setPushNotifications(true);
        return response;
    }
    
    @Override
    public NotificationPreferencesResponse updatePreferences(UUID userId, NotificationPreferencesRequest request) {
        // TODO: Implement preferences update
        return getPreferences(userId);
    }
    
    @Override
    public NotificationStatisticsResponse getStatistics(UUID userId) {
        String query = "SELECT COUNT(*) as total, " +
                      "COUNT(*) FILTER (WHERE is_read = false) as unread, " +
                      "COUNT(*) FILTER (WHERE is_read = true) as read, " +
                      "COUNT(*) FILTER (WHERE priority = 'URGENT') as urgent " +
                      "FROM notifications WHERE user_id = :userId";
        
        Object[] result = (Object[]) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .getSingleResult();
        
        NotificationStatisticsResponse response = new NotificationStatisticsResponse();
        response.setTotalNotifications(((Number) result[0]).intValue());
        response.setUnreadCount(((Number) result[1]).intValue());
        response.setReadCount(((Number) result[2]).intValue());
        response.setUrgentCount(((Number) result[3]).intValue());
        
        return response;
    }
    
    @Override
    public NotificationResponse sendNotification(
            UUID userId, String title, String message, String type, 
            String priority, Map<String, Object> data, String actionUrl) {
        
        logger.info("Sending notification to user " + userId + ": " + type);
        
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
    
    @Override
    public int broadcastNotification(
            List<UUID> userIds, String title, String message, 
            String type, String priority, Map<String, Object> data) {
        
        logger.info("Broadcasting notification to " + userIds.size() + " users");
        
        int sentCount = 0;
        for (UUID userId : userIds) {
            try {
                sendNotification(userId, title, message, type, priority, data, null);
                sentCount++;
            } catch (Exception e) {
                logger.warning("Failed to send notification to user " + userId + ": " + e.getMessage());
            }
        }
        
        return sentCount;
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    private UUID createNotification(
            UUID userId, String title, String message, String type, String priority,
            Map<String, Object> data, String actionUrl, UUID bookingId, UUID routeId) {
        
        String query = "SELECT create_notification(:userId, :title, :message, :type, " +
                      ":data::jsonb, :priority, :actionUrl, :bookingId, :routeId)";
        
        UUID notificationId = (UUID) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .setParameter("title", title)
            .setParameter("message", message)
            .setParameter("type", type)
            .setParameter("data", data != null ? convertToJson(data) : null)
            .setParameter("priority", priority != null ? priority : "NORMAL")
            .setParameter("actionUrl", actionUrl)
            .setParameter("bookingId", bookingId)
            .setParameter("routeId", routeId)
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
            logger.warning("Failed to send SMS notification: " + e.getMessage());
        }
    }
    
    private NotificationResponse mapToNotificationResponse(Object[] row) {
        NotificationResponse response = new NotificationResponse();
        response.setId((UUID) row[0]);
        response.setTitle((String) row[2]);
        response.setMessage((String) row[3]);
        response.setType((String) row[4]);
        response.setPriority((String) row[6]);
        response.setIsRead((Boolean) row[7]);
        response.setReadAt((LocalDateTime) row[8]);
        response.setActionUrl((String) row[9]);
        response.setCreatedAt((LocalDateTime) row[13]);
        
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