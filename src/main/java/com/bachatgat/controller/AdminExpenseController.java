package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.DistributionRequest;
import com.bachatgat.model.GroupExpense;
import com.bachatgat.model.Transaction;
import com.bachatgat.model.TransactionType;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.security.GroupSecurityService;
import com.bachatgat.security.UserPrincipal;
import com.bachatgat.service.AuditService;
import com.bachatgat.service.DistributionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/groups/{groupId}/expenses")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PRESIDENT')")
public class AdminExpenseController {

    private final FirestoreDataService dataService;
    private final DistributionService distributionService;
    private final AuditService auditService;
    private final GroupSecurityService groupSecurityService;

    public AdminExpenseController(FirestoreDataService dataService,
                                  DistributionService distributionService,
                                  AuditService auditService,
                                  GroupSecurityService groupSecurityService) {
        this.dataService = dataService;
        this.distributionService = distributionService;
        this.auditService = auditService;
        this.groupSecurityService = groupSecurityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupExpense>>> getExpenses(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        List<GroupExpense> expenses = dataService.getExpensesByGroupId(groupId);
        return ResponseEntity.ok(ApiResponse.ok(expenses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GroupExpense>> createExpense(
            @PathVariable String groupId,
            @RequestBody GroupExpense expense,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        expense.setGroupId(groupId);
        String username = principal != null ? principal.getUsername() : "ADMIN";
        String userId = principal != null ? principal.getId() : "admin-001";
        LocalDate expenseDate = expense.getExpenseDate() != null ? expense.getExpenseDate() : LocalDate.now();

        GroupExpense existing = dataService.getExpensesByGroupId(groupId).stream()
                    .filter(e -> (expense.getReference() != null && !expense.getReference().isBlank()
                            && expense.getReference().equalsIgnoreCase(e.getReference()))
                            || ((expense.getReference() == null || expense.getReference().isBlank())
                            && expense.getAmount() != null && expense.getAmount().compareTo(e.getAmount()) == 0
                            && expenseDate.equals(e.getExpenseDate())
                            && java.util.Objects.equals(expense.getCategory(), e.getCategory())
                            && java.util.Objects.equals(expense.getDescription(), e.getDescription())))
                    .findFirst().orElse(null);
        if (existing != null) {
            return ResponseEntity.ok(ApiResponse.ok("Expense request already processed", existing));
        }
        expense.setRecordedBy(username);
        expense.setDistributionRule("EQUAL_ACTIVE_MEMBERS");
        expense.setExpenseDate(expenseDate);
        GroupExpense saved = dataService.saveExpense(expense);

        // Record in transaction ledger
        String txnId = "TXN-EXP-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction txn = new Transaction(
                txnId,
                groupId,
                null,
                null,
                null,
                TransactionType.OTHER_EXPENSE,
                saved.getAmount(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                saved.getExpenseDate(),
                saved.getId(),
                "Expense: " + saved.getCategory() + " - " + saved.getDescription(),
                saved.getPaymentMode(),
                username
        );
        txn.setCategory("OTHER_EXPENSE");
        dataService.saveTransaction(txn);

        auditService.log(
                groupId,
                userId,
                username,
                "EXPENSE_RECORDED",
                "GroupExpense",
                saved.getId(),
                null,
                "Amount: " + saved.getAmount() + ", Category: " + saved.getCategory(),
                "127.0.0.1"
        );

        // Every other expense is allocated by the backend immediately.
        saved = distributionService.distributeExpense(
                groupId, saved.getId(), new DistributionRequest("EQUAL_ACTIVE_MEMBERS"), username);
        return ResponseEntity.ok(ApiResponse.ok("Expense recorded and allocated successfully", saved));
    }

    @PostMapping("/{expenseId}/distribute")
    public ResponseEntity<ApiResponse<GroupExpense>> distributeExpense(
            @PathVariable String groupId,
            @PathVariable String expenseId,
            @Valid @RequestBody DistributionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        String username = principal != null ? principal.getUsername() : "ADMIN";
        GroupExpense distributed = distributionService.distributeExpense(groupId, expenseId, request, username);
        return ResponseEntity.ok(ApiResponse.ok("Expense successfully distributed among group members", distributed));
    }
}

