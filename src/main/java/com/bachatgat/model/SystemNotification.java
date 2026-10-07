package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.time.LocalDateTime;

public class SystemNotification {
    @DocumentId
    private String id;
    private String groupId;
    private String targetUserId;
    private String targetRole;
    private String title;
    private String message;
    private String type;
    private boolean read = false;
    private LocalDateTime createdAt = LocalDateTime.now();

    public SystemNotification() {}

    public SystemNotification(String groupId, String targetUserId, String targetRole, String title, String message, String type) {
        this.id = java.util.UUID.randomUUID().toString();
        this.groupId = groupId;
        this.targetUserId = targetUserId;
        this.targetRole = targetRole;
        this.title = title;
        this.message = message;
        this.type = type;
        this.read = false;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
