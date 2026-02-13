package com.commute.metrosync.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Notification Response DTOs
 * Used by NotificationManagementService for API responses
 */
public class NotificationDTOs {
    
    // ==================== NOTIFICATION RESPONSE ====================
    
    public static class NotificationResponse {
        private UUID id;
        private String title;
        private String message;
        private String type;
        private String priority;
        private Boolean isRead;
        private LocalDateTime readAt;
        private LocalDateTime createdAt;
        private Map<String, Object> data;
        private String actionUrl;
        private UUID bookingId;
        private UUID transactionId;
        
        // Getters and Setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        
        public Boolean getIsRead() { return isRead; }
        public void setIsRead(Boolean isRead) { this.isRead = isRead; }
        
        public LocalDateTime getReadAt() { return readAt; }
        public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
        
        public String getActionUrl() { return actionUrl; }
        public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }
        
        public UUID getBookingId() { return bookingId; }
        public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
        
        public UUID getTransactionId() { return transactionId; }
        public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
    }
    
    // ==================== MARK READ RESPONSE ====================
    
    public static class MarkReadResponse {
        private UUID notificationId;
        private Boolean isRead;
        private LocalDateTime readAt;
        
        public UUID getNotificationId() { return notificationId; }
        public void setNotificationId(UUID notificationId) { this.notificationId = notificationId; }
        
        public Boolean getIsRead() { return isRead; }
        public void setIsRead(Boolean isRead) { this.isRead = isRead; }
        
        public LocalDateTime getReadAt() { return readAt; }
        public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    }
    
    // ==================== PREFERENCES REQUEST ====================
    
    public static class NotificationPreferencesRequest {
        private Boolean emailNotifications;
        private Boolean smsNotifications;
        private Boolean pushNotifications;
        private Boolean bookingNotifications;
        private Boolean paymentNotifications;
        private Boolean promotionalNotifications;
        
        public Boolean getEmailNotifications() { return emailNotifications; }
        public void setEmailNotifications(Boolean emailNotifications) { 
            this.emailNotifications = emailNotifications; 
        }
        
        public Boolean getSmsNotifications() { return smsNotifications; }
        public void setSmsNotifications(Boolean smsNotifications) { 
            this.smsNotifications = smsNotifications; 
        }
        
        public Boolean getPushNotifications() { return pushNotifications; }
        public void setPushNotifications(Boolean pushNotifications) { 
            this.pushNotifications = pushNotifications; 
        }
        
        public Boolean getBookingNotifications() { return bookingNotifications; }
        public void setBookingNotifications(Boolean bookingNotifications) { 
            this.bookingNotifications = bookingNotifications; 
        }
        
        public Boolean getPaymentNotifications() { return paymentNotifications; }
        public void setPaymentNotifications(Boolean paymentNotifications) { 
            this.paymentNotifications = paymentNotifications; 
        }
        
        public Boolean getPromotionalNotifications() { return promotionalNotifications; }
        public void setPromotionalNotifications(Boolean promotionalNotifications) { 
            this.promotionalNotifications = promotionalNotifications; 
        }
    }
    
    // ==================== PREFERENCES RESPONSE ====================
    
    public static class NotificationPreferencesResponse {
        private Boolean emailNotifications;
        private Boolean smsNotifications;
        private Boolean pushNotifications;
        private Boolean bookingNotifications;
        private Boolean paymentNotifications;
        private Boolean promotionalNotifications;
        
        public Boolean getEmailNotifications() { return emailNotifications; }
        public void setEmailNotifications(Boolean emailNotifications) { 
            this.emailNotifications = emailNotifications; 
        }
        
        public Boolean getSmsNotifications() { return smsNotifications; }
        public void setSmsNotifications(Boolean smsNotifications) { 
            this.smsNotifications = smsNotifications; 
        }
        
        public Boolean getPushNotifications() { return pushNotifications; }
        public void setPushNotifications(Boolean pushNotifications) { 
            this.pushNotifications = pushNotifications; 
        }
        
        public Boolean getBookingNotifications() { return bookingNotifications; }
        public void setBookingNotifications(Boolean bookingNotifications) { 
            this.bookingNotifications = bookingNotifications; }
        
        public Boolean getPaymentNotifications() { return paymentNotifications; }
        public void setPaymentNotifications(Boolean paymentNotifications) { 
            this.paymentNotifications = paymentNotifications; 
        }
        
        public Boolean getPromotionalNotifications() { return promotionalNotifications; }
        public void setPromotionalNotifications(Boolean promotionalNotifications) { 
            this.promotionalNotifications = promotionalNotifications; 
        }
    }
    
    // ==================== STATISTICS RESPONSE ====================
    
    public static class NotificationStatisticsResponse {
        private Integer totalNotifications;
        private Integer unreadCount;
        private Integer readCount;
        private Integer urgentCount;
        private Integer todayCount;
        private Integer weekCount;
        
        public Integer getTotalNotifications() { return totalNotifications; }
        public void setTotalNotifications(Integer totalNotifications) { 
            this.totalNotifications = totalNotifications; 
        }
        
        public Integer getUnreadCount() { return unreadCount; }
        public void setUnreadCount(Integer unreadCount) { 
            this.unreadCount = unreadCount; 
        }
        
        public Integer getReadCount() { return readCount; }
        public void setReadCount(Integer readCount) { 
            this.readCount = readCount; 
        }
        
        public Integer getUrgentCount() { return urgentCount; }
        public void setUrgentCount(Integer urgentCount) { 
            this.urgentCount = urgentCount; 
        }
        
        public Integer getTodayCount() { return todayCount; }
        public void setTodayCount(Integer todayCount) { 
            this.todayCount = todayCount; 
        }
        
        public Integer getWeekCount() { return weekCount; }
        public void setWeekCount(Integer weekCount) { 
            this.weekCount = weekCount; 
        }
    }
}