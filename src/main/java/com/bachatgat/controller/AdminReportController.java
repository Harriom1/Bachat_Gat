package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.model.Member;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.MemberService;
import com.bachatgat.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PRESIDENT', 'SECRETARY', 'TREASURER')")
public class AdminReportController {

    private final ReportService reportService;
    private final GroupSecurityService groupSecurityService;
    private final MemberService memberService;

    public AdminReportController(ReportService reportService,
                                 GroupSecurityService groupSecurityService,
                                 MemberService memberService) {
        this.reportService = reportService;
        this.groupSecurityService = groupSecurityService;
        this.memberService = memberService;
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMonthlyReport(
            @RequestParam String groupId,
            @RequestParam int month,
            @RequestParam int year,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        Map<String, Object> report = reportService.getMonthlyReport(groupId, month, year);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFinancialSummary(
            @RequestParam String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        Map<String, Object> summary = reportService.getGroupFinancialSummary(groupId);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/member-statement/{memberId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMemberStatement(
            @PathVariable String memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Member member = memberService.getMemberByMemberId(memberId);
        groupSecurityService.validateGroupAccess(principal, member.getGroupId());
        Map<String, Object> statement = reportService.getMemberStatement(memberId);
        return ResponseEntity.ok(ApiResponse.ok(statement));
    }

    @GetMapping("/yearly")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getYearlyReport(
            @RequestParam String groupId,
            @RequestParam int year,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        Map<String, Object> report = reportService.getYearlyReport(groupId, year);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @GetMapping("/outstanding")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOutstandingReport(
            @RequestParam String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        Map<String, Object> report = reportService.getOutstandingReport(groupId);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @PostMapping("/share-whatsapp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> shareReportWhatsApp(
            @RequestParam String groupId,
            @jakarta.validation.Valid @RequestBody com.bachatgat.dto.ReportShareRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        String username = principal != null ? principal.getUsername() : "ADMIN";
        Map<String, Object> res = reportService.shareReportWhatsApp(groupId, request, username);
        return ResponseEntity.ok(ApiResponse.ok("Report shared via WhatsApp successfully", res));
    }

    @PostMapping("/send-email")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendReportEmail(
            @RequestParam String groupId,
            @jakarta.validation.Valid @RequestBody com.bachatgat.dto.ReportShareRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        String username = principal != null ? principal.getUsername() : "ADMIN";
        Map<String, Object> res = reportService.sendReportEmail(groupId, request, username);
        return ResponseEntity.ok(ApiResponse.ok("Report queued for email delivery", res));
    }
}

