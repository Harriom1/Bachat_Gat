package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.GroupDTO;
import com.bachatgat.dto.GroupBusinessRulesRequest;
import com.bachatgat.dto.GroupDropdownDTO;
import com.bachatgat.dto.OfficeBearerDTO;
import com.bachatgat.model.Group;
import com.bachatgat.model.Role;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.GroupService;
import com.bachatgat.service.OtpVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/groups")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PRESIDENT', 'SECRETARY')")
public class AdminGroupController {

    private final GroupService groupService;
    private final GroupSecurityService groupSecurityService;
    private final OtpVerificationService otpVerificationService;

    public AdminGroupController(GroupService groupService, GroupSecurityService groupSecurityService,
                                OtpVerificationService otpVerificationService) {
        this.groupService = groupService;
        this.groupSecurityService = groupSecurityService;
        this.otpVerificationService = otpVerificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Group>>> getAllGroups(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            Group group = groupService.getGroupById(principal.getGroupId());
            return ResponseEntity.ok(ApiResponse.ok(List.of(group)));
        }
        List<Group> groups = groupService.getAllGroups();
        return ResponseEntity.ok(ApiResponse.ok(groups));
    }

    @GetMapping("/archived")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Group>>> getArchivedGroups() {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getArchivedGroups()));
    }

    @GetMapping("/dropdown")
    public ResponseEntity<ApiResponse<List<GroupDropdownDTO>>> getGroupDropdown(@AuthenticationPrincipal UserPrincipal principal) {
        List<Group> groups;
        if (principal.getRole() == Role.ADMIN) {
            groups = List.of(groupService.getGroupById(principal.getGroupId()));
        } else {
            groups = groupService.getAllGroups();
        }

        List<GroupDropdownDTO> dropdown = groups.stream()
                .map(g -> new GroupDropdownDTO(
                        g.getId(),
                        g.getGroupCode() != null ? g.getGroupCode() : g.getId(),
                        g.getGroupName(),
                        g.getGroupNameMr() != null ? g.getGroupNameMr() : g.getGroupName(),
                        g.getGroupNameHi() != null ? g.getGroupNameHi() : g.getGroupName()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(dropdown));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Group>> getGroupById(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, id);
        Group group = groupService.getGroupById(id);
        return ResponseEntity.ok(ApiResponse.ok(group));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Group>> createGroup(
            @Valid @RequestBody GroupDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (dto.getVerificationSessionId() == null || dto.getEmailVerificationId() == null
                || dto.getMobileVerificationId() == null) {
            throw new IllegalArgumentException("Email and mobile verification are required before creating a new group.");
        }
        otpVerificationService.requireVerified(dto.getEmailVerificationId(), dto.getVerificationSessionId(),
                "EMAIL", dto.getPresidentEmail());
        otpVerificationService.requireVerified(dto.getMobileVerificationId(), dto.getVerificationSessionId(),
                "FIREBASE_PHONE", dto.getPresidentMobile());
        Group created = groupService.createGroup(dto, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Group created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Group>> updateGroup(
            @PathVariable String id,
            @Valid @RequestBody GroupDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, id);
        Group updated = groupService.updateGroup(id, dto, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Group updated successfully", updated));
    }

    @PatchMapping("/{id}/business-rules")
    public ResponseEntity<ApiResponse<Group>> updateBusinessRules(
            @PathVariable String id, @Valid @RequestBody GroupBusinessRulesRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, id);
        return ResponseEntity.ok(ApiResponse.ok("Group business rules saved",
                groupService.updateBusinessRules(id, request, principal.getUsername())));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Group>> archiveGroup(@PathVariable String id,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Group archived successfully",
                groupService.archiveGroup(id, principal.getId())));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Group>> restoreGroup(@PathVariable String id,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Group restored successfully",
                groupService.restoreGroup(id, principal.getId())));
    }

    @PostMapping("/{id}/office-bearers")
    public ResponseEntity<ApiResponse<Void>> designateOfficeBearer(
            @PathVariable String id,
            @Valid @RequestBody OfficeBearerDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, id);
        groupService.designateOfficeBearer(id, dto, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Office bearer designated successfully", null));
    }
}

