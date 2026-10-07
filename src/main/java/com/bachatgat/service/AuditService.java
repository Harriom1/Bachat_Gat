package com.bachatgat.service;

import com.bachatgat.model.AuditLog;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private final FirestoreDataService dataService;

    public AuditService(FirestoreDataService dataService) {
        this.dataService = dataService;
    }

    public void log(String groupId, String userId, String username, String action, String entity, String entityId, String oldValue, String newValue, String ipAddress) {
        AuditLog auditLog = new AuditLog(groupId, userId, username, action, entity, entityId, oldValue, newValue, ipAddress);
        dataService.saveAuditLog(auditLog);
    }

    public List<AuditLog> getAuditLogs(String groupId) {
        return dataService.getAuditLogsByGroupId(groupId);
    }
}
