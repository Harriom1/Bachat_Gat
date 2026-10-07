package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.CreateAdminRequest;
import com.bachatgat.exception.DuplicateRecordException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.Group;
import com.bachatgat.model.Role;
import com.bachatgat.model.User;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/client-admins")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminClientAdminController {

    private final FirestoreDataService dataService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminClientAdminController(FirestoreDataService dataService, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.dataService = dataService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllClientAdmins() {
        List<User> admins = dataService.findUsersByRole(Role.ADMIN);
        List<User> presidents = dataService.findUsersByRole(Role.PRESIDENT);
        Map<String, User> combinedMap = new HashMap<>();
        for (User u : admins) combinedMap.put(u.getId(), u);
        for (User u : presidents) combinedMap.put(u.getId(), u);

        // One Group One President: group by groupId to guarantee strictly one president per group
        Map<String, User> groupToPresident = new HashMap<>();
        List<User> unassignedPresidents = new ArrayList<>();

        for (User admin : combinedMap.values()) {
            if (admin.getGroupId() != null && !admin.getGroupId().isBlank()) {
                Group g = dataService.findGroupById(admin.getGroupId()).orElse(null);
                if (g != null) {
                    User current = groupToPresident.get(admin.getGroupId());
                    if (current == null) {
                        groupToPresident.put(admin.getGroupId(), admin);
                    } else if (admin.getId().equals(g.getAssignedAdminId()) || 
                               admin.getUsername().equalsIgnoreCase(g.getPresidentUsername())) {
                        groupToPresident.put(admin.getGroupId(), admin);
                    }
                }
            } else if ("PRESIDENT".equalsIgnoreCase(admin.getDesignation())) {
                unassignedPresidents.add(admin);
            }
        }

        List<User> distinctPresidents = new ArrayList<>(groupToPresident.values());
        distinctPresidents.addAll(unassignedPresidents);

        List<Map<String, Object>> response = distinctPresidents.stream().map(admin -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", admin.getId());
            map.put("username", admin.getUsername());
            map.put("fullName", admin.getFullName());
            map.put("fullNameMr", admin.getFullNameMr());
            map.put("fullNameHi", admin.getFullNameHi());
            map.put("email", admin.getEmail());
            map.put("mobileNumber", admin.getMobileNumber());
            map.put("groupId", admin.getGroupId());
            map.put("active", admin.isActive());
            map.put("firstLogin", admin.isFirstLogin());
            map.put("designation", admin.getDesignation() != null ? admin.getDesignation() : "PRESIDENT");
            map.put("createdAt", admin.getCreatedAt());

            if (admin.getGroupId() != null) {
                dataService.findGroupById(admin.getGroupId()).ifPresent(g -> {
                    map.put("groupName", g.getGroupName());
                    map.put("groupCode", g.getGroupCode());
                    map.put("groupNameMr", g.getGroupNameMr());
                    map.put("groupNameHi", g.getGroupNameHi());
                    map.put("presidentName", g.getPresident() != null ? g.getPresident() : admin.getFullName());
                    map.put("presidentNameMr", g.getPresidentNameMr() != null ? g.getPresidentNameMr() : admin.getFullNameMr());
                    map.put("presidentNameHi", g.getPresidentNameHi() != null ? g.getPresidentNameHi() : admin.getFullNameHi());
                    map.put("activeMembersCount", g.getActiveMembersCount());
                    map.put("totalSavingsBalance", g.getTotalSavingsBalance());
                    map.put("collectionDueDay", g.getCollectionDueDay());
                });
            } else {
                map.put("presidentName", admin.getFullName());
            }
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createClientAdmin(
            @Valid @RequestBody CreateAdminRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        dataService.findUserByUsername(request.getUsername()).ifPresent(u -> {
            throw new DuplicateRecordException("Username '" + request.getUsername() + "' is already in use");
        });

        Group group;
        if (request.getGroupId() != null && !request.getGroupId().isBlank()) {
            group = dataService.findGroupById(request.getGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + request.getGroupId()));

            // One Group One President: Unassign any previous president from this group
            for (User u : dataService.getAllUsers()) {
                if (group.getId().equals(u.getGroupId()) && ("PRESIDENT".equalsIgnoreCase(u.getDesignation()) || u.getRole() == Role.ADMIN)) {
                    if (!u.getUsername().equalsIgnoreCase(request.getUsername())) {
                        u.setGroupId(null);
                        u.setDesignation("FORMER_PRESIDENT");
                        dataService.saveUser(u);
                    }
                }
            }
        } else if (request.isCreateNewGroup() || (request.getGroupName() != null && !request.getGroupName().isBlank())) {
            group = new Group();
            group.setGroupName(request.getGroupName().trim());
            group.setGroupNameMr(request.getGroupNameMr());
            group.setGroupNameHi(request.getGroupNameHi());
            group.setGroupCode(dataService.generateNextGroupCode());
            group.setRegistrationId(dataService.generateNextGroupRegistrationId());
            group.setMonthlyShareAmount(request.getMonthlyShareAmount() != null ? request.getMonthlyShareAmount() : java.math.BigDecimal.valueOf(3300));
            group.setCollectionDueDay(request.getCollectionDueDay() > 0 ? request.getCollectionDueDay() : 10);
            group.setPresident(request.getFullName().trim());
            group.setPresidentNameMr(request.getFullNameMr());
            group.setPresidentNameHi(request.getFullNameHi());
            group.setPresidentMobile(request.getMobileNumber());
            group.setPresidentUsername(request.getUsername());
            group.setStatus("ACTIVE");
            group.setCreatedBy(principal.getUsername());
            group = dataService.saveGroup(group);
        } else {
            throw new IllegalArgumentException("Either an existing Assigned Group ID or new Group Name is required.");
        }

        User admin = new User(
                request.getUsername().trim(),
                passwordEncoder.encode(request.getTemporaryPassword()),
                request.getEmail(),
                request.getFullName().trim(),
                Role.ADMIN,
                group.getId(),
                null
        );
        admin.setFullNameMr(request.getFullNameMr());
        admin.setFullNameHi(request.getFullNameHi());
        admin.setMobileNumber(request.getMobileNumber());
        admin.setDesignation("PRESIDENT");
        admin.setFirstLogin(false); // Enable immediate login with provided credentials

        User saved = dataService.saveUser(admin);

        // Update group assigned admin and president identity
        group.setAssignedAdminId(saved.getId());
        group.setAssignedAdminName(saved.getFullName());
        group.setPresident(saved.getFullName());
        group.setPresidentNameMr(saved.getFullNameMr());
        group.setPresidentNameHi(saved.getFullNameHi());
        group.setPresidentMobile(saved.getMobileNumber());
        group.setPresidentUsername(saved.getUsername());
        dataService.saveGroup(group);

        auditService.log(group.getId(), principal.getUsername(), principal.getUsername(),
                "CLIENT_ADMIN_CREATED", "USER", saved.getId(), null,
                "Assigned Group President " + saved.getUsername() + " to " + group.getGroupName(), "127.0.0.1");

        Map<String, Object> res = new HashMap<>();
        res.put("id", saved.getId());
        res.put("username", saved.getUsername());
        res.put("fullName", saved.getFullName());
        res.put("groupId", saved.getGroupId());
        res.put("designation", "PRESIDENT");
        res.put("firstLogin", false);
        res.put("message", "Client Admin / President created successfully");

        return ResponseEntity.ok(ApiResponse.ok("Client Admin created successfully", res));
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable String userId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        String temporaryPassword = body.get("temporaryPassword");
        if (temporaryPassword == null || temporaryPassword.isBlank()) {
            temporaryPassword = body.get("newPassword");
        }
        if (temporaryPassword == null || temporaryPassword.trim().length() < 6) {
            throw new IllegalArgumentException("Temporary password must be at least 6 characters");
        }
        temporaryPassword = temporaryPassword.trim();

        User user = dataService.findUserById(userId)
                .or(() -> dataService.findUserByUsername(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID or username: " + userId));

        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setFirstLogin(false); // Usable immediately upon reset
        dataService.saveUser(user);

        auditService.log(user.getGroupId(), principal.getUsername(), principal.getUsername(),
                "CLIENT_ADMIN_PASSWORD_RESET", "USER", user.getId(), null,
                "Reset password for " + user.getUsername(), "127.0.0.1");

        return ResponseEntity.ok(ApiResponse.ok("Password reset successfully. Credentials are now active.", null));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<Void>> toggleStatus(
            @PathVariable String userId,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean active = body.getOrDefault("active", true);
        User user = dataService.findUserById(userId)
                .or(() -> dataService.findUserByUsername(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID or username: " + userId));

        user.setActive(active);
        dataService.saveUser(user);

        auditService.log(user.getGroupId(), principal.getUsername(), principal.getUsername(),
                "CLIENT_ADMIN_STATUS_CHANGED", "USER", user.getId(), null,
                "Changed active status to " + active, "127.0.0.1");

        return ResponseEntity.ok(ApiResponse.ok("Admin account status updated", null));
    }
}
