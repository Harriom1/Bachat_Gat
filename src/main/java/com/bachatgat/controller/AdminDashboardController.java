package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.DashboardAdminDTO;
import com.bachatgat.model.Role;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final GroupSecurityService groupSecurityService;

    public AdminDashboardController(DashboardService dashboardService, GroupSecurityService groupSecurityService) {
        this.dashboardService = dashboardService;
        this.groupSecurityService = groupSecurityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardAdminDTO>> getDashboard(
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
        DashboardAdminDTO dto = dashboardService.getAdminDashboard(effectiveGroupId);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }
}

