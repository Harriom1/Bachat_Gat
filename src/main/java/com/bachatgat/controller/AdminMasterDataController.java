package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.MasterDataDTO;
import com.bachatgat.model.GroupMasterData;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.MasterDataService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/groups/{groupId}/master-data")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PRESIDENT')")
public class AdminMasterDataController {

    private final MasterDataService masterDataService;
    private final GroupSecurityService groupSecurityService;

    public AdminMasterDataController(MasterDataService masterDataService,
                                     GroupSecurityService groupSecurityService) {
        this.masterDataService = masterDataService;
        this.groupSecurityService = groupSecurityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GroupMasterData>> getMasterData(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        GroupMasterData md = masterDataService.getLatestMasterData(groupId);
        return ResponseEntity.ok(ApiResponse.ok(md));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<GroupMasterData>>> getMasterDataHistory(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        List<GroupMasterData> history = masterDataService.getMasterDataHistory(groupId);
        return ResponseEntity.ok(ApiResponse.ok(history));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<GroupMasterData>> updateMasterData(
            @PathVariable String groupId,
            @Valid @RequestBody MasterDataDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        String username = principal != null ? principal.getUsername() : "ADMIN";
        GroupMasterData updated = masterDataService.updateMasterData(groupId, dto, username);
        return ResponseEntity.ok(ApiResponse.ok("Group master data updated to version " + updated.getVersion(), updated));
    }
}
