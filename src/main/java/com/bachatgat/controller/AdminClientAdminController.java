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
import com.bachatgat.service.OtpVerificationService;
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
    private final OtpVerificationService otpVerificationService;

    public AdminClientAdminController(FirestoreDataService dataService, PasswordEncoder passwordEncoder, AuditService auditService,
                                      OtpVerificationService otpVerificationService) {
        this.dataService = dataService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.otpVerificationService = otpVerificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllClientAdmins(
            @RequestParam(defaultValue = "false") boolean includeGroupProfiles) {
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
                dataService.findGroupById(admin.getGroupId()).ifPresent(group -> putGroupDetails(map, group, admin));
            } else {
                map.put("presidentName", admin.getFullName());
            }
            return map;
        }).collect(Collectors.toList());

        if (includeGroupProfiles) {
            for (Group group : dataService.getAllGroups()) {
                if (groupToPresident.containsKey(group.getId()) || !"ACTIVE".equalsIgnoreCase(group.getStatus())
                        || group.getPresident() == null || group.getPresident().isBlank()) {
                    continue;
                }
                // An existing profile is useful even when its login belongs to another group.
                // Do not expose that account's ID or imply that it is active for this group.
                Map<String, Object> row = new HashMap<>();
                row.put("groupId", group.getId());
                row.put("username", group.getPresidentUsername());
                row.put("mobileNumber", group.getPresidentMobile());
                row.put("active", false);
                row.put("firstLogin", false);
                row.put("profileOnly", true);
                putGroupDetails(row, group, null);
                response.add(row);
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private void putGroupDetails(Map<String, Object> row, Group group, User admin) {
        row.put("groupName", group.getGroupName());
        row.put("groupCode", group.getGroupCode());
        row.put("groupNameMr", group.getGroupNameMr());
        row.put("groupNameHi", group.getGroupNameHi());
        row.put("presidentName", group.getPresident() != null || admin == null ? group.getPresident() : admin.getFullName());
        row.put("presidentNameMr", group.getPresidentNameMr() != null || admin == null ? group.getPresidentNameMr() : admin.getFullNameMr());
        row.put("presidentNameHi", group.getPresidentNameHi() != null || admin == null ? group.getPresidentNameHi() : admin.getFullNameHi());
        row.put("activeMembersCount", group.getActiveMembersCount());
        row.put("totalSavingsBalance", group.getTotalSavingsBalance());
        row.put("collectionDueDay", group.getCollectionDueDay());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createClientAdmin(
            @Valid @RequestBody CreateAdminRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean creatingNewGroup = request.isCreateNewGroup()
                || (request.getGroupId() == null && request.getGroupName() != null && !request.getGroupName().isBlank());
        if (creatingNewGroup) {
            if (request.getVerificationSessionId() == null || request.getEmailVerificationId() == null
                    || request.getMobileVerificationId() == null) {
                throw new IllegalArgumentException("Email and mobile verification are required before creating a new group.");
            }
            otpVerificationService.requireVerified(request.getEmailVerificationId(), request.getVerificationSessionId(),
                    "EMAIL", request.getEmail());
            otpVerificationService.requireVerified(request.getMobileVerificationId(), request.getVerificationSessionId(),
                    "FIREBASE_PHONE", request.getMobileNumber());
        }

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
