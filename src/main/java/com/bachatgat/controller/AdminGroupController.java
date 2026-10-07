package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.GroupDTO;
import com.bachatgat.dto.GroupDropdownDTO;
import com.bachatgat.model.Group;
import com.bachatgat.model.Role;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/groups")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PRESIDENT')")
public class AdminGroupController {

    private final GroupService groupService;
    private final GroupSecurityService groupSecurityService;

    public AdminGroupController(GroupService groupService, GroupSecurityService groupSecurityService) {
        this.groupService = groupService;
        this.groupSecurityService = groupSecurityService;
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

    @PostMapping("/{id}/office-bearers")
    public ResponseEntity<ApiResponse<com.bachatgat.model.User>> designateOfficeBearer(
            @PathVariable String id,
            @Valid @RequestBody com.bachatgat.dto.OfficeBearerDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, id);
        com.bachatgat.model.User user = groupService.designateOfficeBearer(id, dto, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Office bearer designated successfully", user));
    }
}

