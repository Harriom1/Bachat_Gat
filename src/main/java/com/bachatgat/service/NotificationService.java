package com.bachatgat.service;

import com.bachatgat.model.SystemNotification;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final FirestoreDataService dataService;

    public NotificationService(FirestoreDataService dataService) {
        this.dataService = dataService;
    }

    public SystemNotification sendNotification(String groupId, String targetUserId, String targetRole, String title, String message, String type) {
        SystemNotification notification = new SystemNotification(groupId, targetUserId, targetRole, title, message, type);
        return dataService.saveNotification(notification);
    }

    public List<SystemNotification> getNotifications(String userId, String role, String groupId) {
        return dataService.getNotificationsForUser(userId, role, groupId);
    }

    public void markAsRead(String notificationId) {
        dataService.getNotificationsForUser(null, null, null).stream()
                .filter(n -> n.getId().equals(notificationId))
                .findFirst()
                .ifPresent(n -> {
                    n.setRead(true);
                    dataService.saveNotification(n);
                });
    }
}
