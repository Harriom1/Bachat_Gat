package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.CreateMemberLoginRequest;
import com.bachatgat.dto.MemberDTO;
import com.bachatgat.model.Member;
import com.bachatgat.model.MemberStatus;
import com.bachatgat.model.Role;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PRESIDENT', 'SECRETARY', 'TREASURER')")
public class AdminMemberController {

    private final MemberService memberService;
    private final GroupSecurityService groupSecurityService;

    public AdminMemberController(MemberService memberService, GroupSecurityService groupSecurityService) {
        this.memberService = memberService;
        this.groupSecurityService = groupSecurityService;
    }

    @GetMapping("/members")
    public ResponseEntity<ApiResponse<List<Member>>> getAllMembers(@AuthenticationPrincipal UserPrincipal principal) {
        List<Member> members = principal.getRole() == Role.SUPER_ADMIN
                ? memberService.getAllMembers()
                : memberService.getMembers(principal.getGroupId());
        return ResponseEntity.ok(ApiResponse.ok(members));
    }

    @GetMapping("/groups/{groupId}/members")
    public ResponseEntity<ApiResponse<List<Member>>> getGroupMembers(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        List<Member> members = memberService.getMembers(groupId);
        return ResponseEntity.ok(ApiResponse.ok(members));
    }

    @PostMapping("/groups/{groupId}/members")
    public ResponseEntity<ApiResponse<Member>> createMember(
            @PathVariable String groupId,
            @Valid @RequestBody MemberDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        dto.setGroupId(groupId);
        Member created = memberService.createMember(dto, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Member registered successfully", created));
    }

    @PostMapping("/groups/{groupId}/members/normalize-registration-ids")
    public ResponseEntity<ApiResponse<List<Member>>> normalizeRegistrationIds(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        List<Member> normalized = memberService.renumberGroupRegistrationIds(groupId, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Member registration IDs normalized for this group", normalized));
    }

    @PostMapping("/groups/{groupId}/enroll-existing-member")
    public ResponseEntity<ApiResponse<Member>> enrollExistingMember(
            @PathVariable String groupId,
            @Valid @RequestBody com.bachatgat.dto.EnrollMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        Member enrolled = memberService.enrollExistingMember(
                groupId,
                request.getMemberId(),
                request.getMonthlyShareAmount(),
                request.getMonthlyCommittedAmount(),
                request.getBachatStartDate(),
                request.getBachatEndDate(),
                principal.getUsername()
        );
        return ResponseEntity.ok(ApiResponse.ok("Member enrolled into group successfully", enrolled));
    }

    @DeleteMapping("/groups/{groupId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMemberFromGroup(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        memberService.removeMemberFromGroup(groupId, memberId, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Member removed from group successfully", null));
    }

    @GetMapping("/members/{id}")
    public ResponseEntity<ApiResponse<Member>> getMemberById(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Member member = memberService.getMemberById(id);
        groupSecurityService.validateGroupAccess(principal, member.getGroupId());
        return ResponseEntity.ok(ApiResponse.ok(member));
    }

    @PutMapping("/members/{id}")
    public ResponseEntity<ApiResponse<Member>> updateMember(
            @PathVariable String id,
            @Valid @RequestBody MemberDTO dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        Member member = memberService.getMemberById(id);
        groupSecurityService.validateGroupAccess(principal, member.getGroupId());
        Member updated = memberService.updateMember(id, dto, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Member details updated successfully", updated));
    }

    @PatchMapping("/members/{id}/status")
    public ResponseEntity<ApiResponse<Member>> updateMemberStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        Member member = memberService.getMemberById(id);
        groupSecurityService.validateGroupAccess(principal, member.getGroupId());
        String statusStr = body.get("status");
        MemberStatus status = MemberStatus.valueOf(statusStr.toUpperCase());
        Member updated = memberService.updateStatus(id, status, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Member status updated to " + status, updated));
    }

    // ==========================================
    // MEMBER LOGIN MANAGEMENT APIs (Requirements 13, 14, 15)
    // ==========================================
    @GetMapping("/members/{id}/login-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMemberLoginStatus(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Member member = memberService.getMemberById(id);
        groupSecurityService.validateGroupAccess(principal, member.getGroupId());
        Map<String, Object> status = memberService.getMemberLoginStatus(id);
        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    @PostMapping("/members/{id}/create-login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createMemberLogin(
            @PathVariable String id,
            @Valid @RequestBody CreateMemberLoginRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Member member = memberService.getMemberById(id);
        groupSecurityService.validateGroupAccess(principal, member.getGroupId());
        Map<String, Object> result = memberService.createMemberLogin(
                id, request.getUsername(), request.getTemporaryPassword(), principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Member login account created successfully", result));
    }

    @PostMapping("/members/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetMemberPassword(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        Member member = memberService.getMemberById(id);
        groupSecurityService.validateGroupAccess(principal, member.getGroupId());
        String tempPass = body.get("temporaryPassword");
        if (tempPass == null || tempPass.isBlank()) {
            tempPass = body.get("newPassword");
        }
        if (tempPass == null || tempPass.length() < 6) {
            throw new IllegalArgumentException("Temporary password must be at least 6 characters");
        }
        memberService.resetMemberPassword(id, tempPass, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Temporary password assigned successfully. Member must change it upon login.", null));
    }

    @PatchMapping("/members/{id}/login-status")
    public ResponseEntity<ApiResponse<Void>> toggleMemberAccountStatus(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        Member member = memberService.getMemberById(id);
        groupSecurityService.validateGroupAccess(principal, member.getGroupId());
        boolean active = body.getOrDefault("active", true);
        memberService.toggleMemberAccountStatus(id, active, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Member login account status updated successfully", null));
    }
}

