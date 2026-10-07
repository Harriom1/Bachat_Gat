package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.LoanExtraPaymentRequest;
import com.bachatgat.dto.LoanRepaymentRequest;
import com.bachatgat.model.*;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/loans")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminLoanController {

    private final LoanService loanService;
    private final GroupSecurityService groupSecurityService;

    public AdminLoanController(LoanService loanService, GroupSecurityService groupSecurityService) {
        this.loanService = loanService;
        this.groupSecurityService = groupSecurityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Loan>>> getLoans(
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (groupId != null && !groupId.isBlank()) {
            groupSecurityService.validateGroupAccess(principal, groupId);
        }
        String effectiveGroupId = (principal.getRole() == Role.ADMIN) ? principal.getGroupId() : groupId;
        List<Loan> loans = loanService.getLoansByGroupId(effectiveGroupId);

        List<Loan> filtered = loans.stream()
                .filter(l -> {
                    if (memberId != null && !memberId.isBlank() && !memberId.equalsIgnoreCase(l.getMemberId())) {
                        return false;
                    }
                    if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
                        if (l.getStatus() == null || !l.getStatus().name().equalsIgnoreCase(status)) {
                            return false;
                        }
                    }
                    if (month != null && month > 0 && year != null && year > 0) {
                        java.time.LocalDate d = l.getDisbursementDate() != null ? l.getDisbursementDate() : l.getCreatedAt().toLocalDate();
                        if (d == null || d.getMonthValue() != month || d.getYear() != year) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted((a, b) -> {
                    java.time.LocalDate d1 = a.getDisbursementDate() != null ? a.getDisbursementDate() : a.getCreatedAt().toLocalDate();
                    java.time.LocalDate d2 = b.getDisbursementDate() != null ? b.getDisbursementDate() : b.getCreatedAt().toLocalDate();
                    if (d1 == null || d2 == null) return 0;
                    return d2.compareTo(d1);
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(filtered));
    }

    /**
     * Monthly Loan Payment Status
     * Lists members who have paid vs not paid EMI for the given month/year.
     */
    @GetMapping("/monthly-status")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getMonthlyLoanPaymentStatus(
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (groupId != null && !groupId.isBlank()) {
            groupSecurityService.validateGroupAccess(principal, groupId);
        }
        String effectiveGroupId = (principal.getRole() == Role.ADMIN) ? principal.getGroupId() : groupId;
        int targetMonth = (month != null && month > 0) ? month : java.time.LocalDate.now().getMonthValue();
        int targetYear = (year != null && year > 0) ? year : java.time.LocalDate.now().getYear();

        List<Loan> loans = loanService.getLoansByGroupId(effectiveGroupId);
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();

        for (Loan l : loans) {
            if (l.getStatus() == LoanStatus.CANCELLED || l.getStatus() == LoanStatus.PENDING) {
                continue;
            }
            List<LoanRepaymentSchedule> schedules = loanService.getLoanSchedule(l.getId());
            LoanRepaymentSchedule matching = schedules.stream()
                    .filter(s -> s.getDueDate() != null && s.getDueDate().getMonthValue() == targetMonth && s.getDueDate().getYear() == targetYear)
                    .findFirst()
                    .orElse(null);

            if (matching == null && l.getStatus() == LoanStatus.ACTIVE) {
                matching = schedules.stream()
                        .filter(s -> s.getStatus() != RepaymentStatus.PAID)
                        .findFirst()
                        .orElse(null);
            }

            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("loanId", l.getLoanId());
            map.put("memberId", l.getMemberId());
            map.put("memberName", l.getMemberName());
            map.put("loanAmount", l.getPrincipalAmount());
            map.put("interestRate", l.getInterestRate());
            map.put("interestType", l.getInterestType());
            map.put("durationMonths", l.getDurationMonths());

            java.math.BigDecimal emi = (matching != null && matching.getEmiAmount() != null) ? matching.getEmiAmount() : l.getMonthlyInstallment();
            java.math.BigDecimal principalPart = (matching != null) ? matching.getPrincipalAmount() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal interestPart = (matching != null) ? matching.getInterestAmount() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal penalty = (matching != null && matching.getPenaltyAmount() != null) ? matching.getPenaltyAmount() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalDue = (matching != null) ? matching.getTotalDue() : emi;
            java.math.BigDecimal paid = (matching != null) ? matching.getPaidAmount() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal remaining = (matching != null) ? matching.getOutstandingAmount() : totalDue;
            java.time.LocalDate dueDate = (matching != null && matching.getDueDate() != null) ? matching.getDueDate() : l.getNextDueDate();
            java.time.LocalDate paidDate = (matching != null) ? matching.getPaidDate() : null;
            java.math.BigDecimal remainingPrincipal = (matching != null && matching.getClosingPrincipal() != null) ? matching.getClosingPrincipal() : l.getOutstandingPrincipal();
            String status = (matching != null) ? matching.getStatus().name() : (l.getStatus() != null ? l.getStatus().name() : "PENDING");

            map.put("emi", emi);
            map.put("principalComponent", principalPart);
            map.put("interestComponent", interestPart);
            map.put("penalty", penalty);
            map.put("totalDue", totalDue);
            map.put("paidAmount", paid);
            map.put("remainingAmount", remaining);
            map.put("dueDate", dueDate != null ? dueDate.toString() : "-");
            map.put("paidDate", paidDate != null ? paidDate.toString() : null);
            map.put("paymentMethod", paidDate != null ? "UPI/CASH" : "-");
            map.put("remainingPrincipal", remainingPrincipal);
            map.put("status", status);

            result.add(map);
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Loan>> getLoanById(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Loan loan = loanService.getLoanById(id);
        groupSecurityService.validateGroupAccess(principal, loan.getGroupId());
        return ResponseEntity.ok(ApiResponse.ok(loan));
    }

    @GetMapping("/{id}/schedule")
    public ResponseEntity<ApiResponse<List<LoanRepaymentSchedule>>> getLoanSchedule(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Loan loan = loanService.getLoanById(id);
        groupSecurityService.validateGroupAccess(principal, loan.getGroupId());
        List<LoanRepaymentSchedule> schedule = loanService.getLoanSchedule(id);
        return ResponseEntity.ok(ApiResponse.ok(schedule));
    }

    @PostMapping("/{id}/repayment")
    public ResponseEntity<ApiResponse<Loan>> recordRepayment(
            @PathVariable String id,
            @Valid @RequestBody LoanRepaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Loan loan = loanService.getLoanById(id);
        groupSecurityService.validateGroupAccess(principal, loan.getGroupId());
        Loan updated = loanService.recordRepayment(id, request, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Repayment recorded successfully", updated));
    }

    @PostMapping("/{id}/extra-payment")
    public ResponseEntity<ApiResponse<Loan>> recordExtraPayment(
            @PathVariable String id,
            @Valid @RequestBody LoanExtraPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Loan loan = loanService.getLoanById(id);
        groupSecurityService.validateGroupAccess(principal, loan.getGroupId());
        Loan updated = loanService.recordExtraPayment(id, request, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Extra prepayment recorded and credited against principal successfully", updated));
    }

    @PostMapping("/{id}/disburse")
    public ResponseEntity<ApiResponse<Loan>> disburseLoan(
            @PathVariable String id,
            @RequestBody(required = false) java.util.Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        Loan loan = loanService.getLoanById(id);
        groupSecurityService.validateGroupAccess(principal, loan.getGroupId());

        java.time.LocalDate date = java.time.LocalDate.now();
        String method = "BANK_TRANSFER";
        String ref = "DISB-" + loan.getLoanId();

        if (body != null) {
            if (body.get("paymentMethod") != null && !body.get("paymentMethod").isBlank()) {
                method = body.get("paymentMethod");
            }
            if (body.get("referenceNumber") != null && !body.get("referenceNumber").isBlank()) {
                ref = body.get("referenceNumber");
            }
            if (body.get("disbursementDate") != null && !body.get("disbursementDate").isBlank()) {
                try {
                    date = java.time.LocalDate.parse(body.get("disbursementDate"));
                } catch (Exception ignored) {}
            }
        }

        Loan disbursed = loanService.disburseLoan(id, date, method, ref, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Loan successfully disbursed. Amount ₹" + disbursed.getPrincipalAmount() + " credited.", disbursed));
    }
}
