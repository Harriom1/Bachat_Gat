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
        List<Transaction> txns = transactionService.getFilteredTransactions(effectiveGroupId, memberId, month, year, type);
        return ResponseEntity.ok(ApiResponse.ok(txns));
    }
}
