package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.LoanApprovalRequest;
import com.bachatgat.model.Loan;
import com.bachatgat.model.LoanApplication;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.LoanApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/loan-applications")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PRESIDENT', 'SECRETARY', 'TREASURER')")
public class AdminLoanApplicationController {

    private final LoanApplicationService loanApplicationService;
    private final GroupSecurityService groupSecurityService;

    public AdminLoanApplicationController(LoanApplicationService loanApplicationService,
                                         GroupSecurityService groupSecurityService) {
        this.loanApplicationService = loanApplicationService;
        this.groupSecurityService = groupSecurityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LoanApplication>>> getApplications(
            @RequestParam(required = false) String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (groupId != null && !groupId.isBlank()) {
            groupSecurityService.validateGroupAccess(principal, groupId);
        }
        String effectiveGroupId = groupSecurityService.resolveEffectiveGroupId(principal, groupId);
        List<LoanApplication> apps = loanApplicationService.getApplicationsByGroupId(effectiveGroupId);
        return ResponseEntity.ok(ApiResponse.ok(apps));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LoanApplication>> getApplicationById(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        LoanApplication app = loanApplicationService.getApplicationById(id);
        groupSecurityService.validateGroupAccess(principal, app.getGroupId());
        return ResponseEntity.ok(ApiResponse.ok(app));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Loan>> approveApplication(
            @PathVariable String id,
            @Valid @RequestBody LoanApprovalRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LoanApplication app = loanApplicationService.getApplicationById(id);
        groupSecurityService.validateGroupAccess(principal, app.getGroupId());
        Loan loan = loanApplicationService.approveApplication(id, request, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Loan approved and disbursed successfully. Loan ID: " + loan.getLoanId(), loan));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<LoanApplication>> rejectApplication(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        LoanApplication app = loanApplicationService.getApplicationById(id);
        groupSecurityService.validateGroupAccess(principal, app.getGroupId());
        String reason = body.getOrDefault("reason", "Application does not meet group loan criteria");
        LoanApplication rejectedApp = loanApplicationService.rejectApplication(id, reason, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Loan application rejected", rejectedApp));
    }
}
