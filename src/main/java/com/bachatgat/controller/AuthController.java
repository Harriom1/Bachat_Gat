package com.bachatgat.controller;

import com.bachatgat.dto.*;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/groups")
    public ResponseEntity<ApiResponse<List<GroupDropdownDTO>>> getPublicGroups() {
        List<GroupDropdownDTO> groups = authService.getPublicGroups();
        return ResponseEntity.ok(ApiResponse.ok(groups));
    }

    @GetMapping("/groups/search")
    public ResponseEntity<ApiResponse<List<GroupDropdownDTO>>> searchPublicGroups(
            @RequestParam(name = "q", required = false) String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.searchPublicGroups(query)));
    }

    @GetMapping("/groups/{groupId}/members")
    public ResponseEntity<ApiResponse<List<MemberDropdownDTO>>> getPublicGroupMembers(@PathVariable String groupId) {
        List<MemberDropdownDTO> members = authService.getPublicGroupMembers(groupId);
        return ResponseEntity.ok(ApiResponse.ok(members));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.ok("Password updated successfully", null));
    }

    @PostMapping("/change-username")
    public ResponseEntity<ApiResponse<Void>> changeUsername(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangeUsernameRequest request) {
        authService.changeUsername(principal.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.ok("Username updated successfully. Please sign in again.", null));
    }

    @PostMapping("/first-login-change-password")
    public ResponseEntity<ApiResponse<Void>> firstLoginChangePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody com.bachatgat.dto.FirstLoginPasswordRequest request) {
        authService.firstLoginChangePassword(principal.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.ok("Password created successfully. You can now access your dashboard.", null));
    }

    @PostMapping("/member-first-login-setup")
    public ResponseEntity<ApiResponse<LoginResponse>> memberFirstLoginSetup(
            @Valid @RequestBody com.bachatgat.dto.MemberFirstLoginSetupRequest request) {
        LoginResponse response = authService.memberFirstLoginSetup(request);
        return ResponseEntity.ok(ApiResponse.ok("Password configured successfully. Welcome to Bachat Gat Portal!", response));
    }
}
