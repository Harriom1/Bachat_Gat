package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.model.AuditLog;
import com.bachatgat.model.Role;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminAuditController {

    private final AuditService auditService;
    private final GroupSecurityService groupSecurityService;

    public AdminAuditController(AuditService auditService, GroupSecurityService groupSecurityService) {
        this.auditService = auditService;
        this.groupSecurityService = groupSecurityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs(
            @RequestParam(required = false) String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        String effectiveGroupId = groupId;
        if (principal.getRole() == Role.ADMIN) {
            if (groupId != null && !groupId.isBlank()) {
                groupSecurityService.validateGroupAccess(principal, groupId);
            }
            effectiveGroupId = principal.getGroupId();
        } else if (groupId != null && !groupId.isBlank()) {
            groupSecurityService.validateGroupAccess(principal, groupId);
        }
        List<AuditLog> logs = auditService.getAuditLogs(effectiveGroupId);
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }
}
