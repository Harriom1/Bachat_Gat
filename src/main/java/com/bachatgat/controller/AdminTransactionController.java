package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.model.Role;
import com.bachatgat.model.Transaction;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/transactions")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminTransactionController {

    private final TransactionService transactionService;
    private final GroupSecurityService groupSecurityService;

    public AdminTransactionController(TransactionService transactionService,
                                      GroupSecurityService groupSecurityService) {
        this.transactionService = transactionService;
        this.groupSecurityService = groupSecurityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Transaction>>> getTransactions(
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal UserPrincipal principal) {
        String effectiveGroupId = groupId;
        if (principal.getRole() == Role.ADMIN) {
            if (groupId != null && !groupId.isBlank()) {
                groupSecurityService.validateGroupAccess(principal, groupId);
            }
            effectiveGroupId = principal.getGroupId();
        } else if (groupId != null && !groupId.isBlank()) {
            groupSecurityService.validateGroupAccess(principal, groupId);
        }
        List<Transaction> txns = transactionService.getDisplayTransactions(effectiveGroupId).stream()
                .filter(t -> matchesFilter(t, memberId, month, year, type))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(txns));
    }

    private boolean matchesFilter(Transaction t, String memberId, Integer month, Integer year, String type) {
        if (memberId != null && !memberId.isBlank() && !memberId.equalsIgnoreCase(t.getMemberId())) return false;
        if (t.getDate() == null) return false;
        if (month != null && month > 0 && t.getDate().getMonthValue() != month) return false;
        if (year != null && year > 0 && t.getDate().getYear() != year) return false;
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) return true;
        if ("MEMBER_SAVING".equalsIgnoreCase(type)) return t.getType() == com.bachatgat.model.TransactionType.MEMBER_SAVING
                || t.getType() == com.bachatgat.model.TransactionType.SHARE_CONTRIBUTION
                || t.getType() == com.bachatgat.model.TransactionType.SHARE_COLLECTION;
        if ("LOAN_REPAYMENT".equalsIgnoreCase(type)) return t.getType() == com.bachatgat.model.TransactionType.LOAN_REPAYMENT
                || t.getType() == com.bachatgat.model.TransactionType.LOAN_PRINCIPAL_PAYMENT
                || t.getType() == com.bachatgat.model.TransactionType.LOAN_PRINCIPAL_REPAYMENT
                || t.getType() == com.bachatgat.model.TransactionType.LOAN_INTEREST_PAYMENT;
        if ("EXTRA_LOAN_PAYMENT".equalsIgnoreCase(type)) return t.getType() == com.bachatgat.model.TransactionType.EXTRA_LOAN_PAYMENT
                || t.getType() == com.bachatgat.model.TransactionType.LOAN_EXTRA_PAYMENT;
        if ("OTHER_INCOME".equalsIgnoreCase(type)) return t.getType() == com.bachatgat.model.TransactionType.OTHER_INCOME
                || t.getType() == com.bachatgat.model.TransactionType.LOAN_INTEREST_INCOME
                || t.getType() == com.bachatgat.model.TransactionType.LOAN_PENALTY_INCOME
                || t.getType() == com.bachatgat.model.TransactionType.SHARE_LATE_FEE;
        return t.getType() != null && t.getType().name().equalsIgnoreCase(type);
    }
}
