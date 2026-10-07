package com.bachatgat.controller;

import com.bachatgat.dto.ApiResponse;
import com.bachatgat.dto.DistributionRequest;
import com.bachatgat.dto.IncomeRequest;
import com.bachatgat.model.GroupIncome;
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
@RequestMapping("/api/admin/groups/{groupId}/income")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PRESIDENT')")
public class AdminIncomeController {

    private final FirestoreDataService dataService;
    private final DistributionService distributionService;
    private final AuditService auditService;
    private final GroupSecurityService groupSecurityService;

    public AdminIncomeController(FirestoreDataService dataService,
                                 DistributionService distributionService,
                                 AuditService auditService,
                                 GroupSecurityService groupSecurityService) {
        this.dataService = dataService;
        this.distributionService = distributionService;
        this.auditService = auditService;
        this.groupSecurityService = groupSecurityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupIncome>>> getIncomes(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        List<GroupIncome> incomes = dataService.getIncomesByGroupId(groupId);
        return ResponseEntity.ok(ApiResponse.ok(incomes));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GroupIncome>> recordIncome(
            @PathVariable String groupId,
            @Valid @RequestBody IncomeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        String username = principal != null ? principal.getUsername() : "ADMIN";
        String userId = principal != null ? principal.getId() : "admin-001";
        LocalDate incomeDate = request.getIncomeDate() != null ? request.getIncomeDate() : LocalDate.now();

        // Distribution is a fixed business rule. The browser is not allowed
        // to choose whether income is shared or retained.
        String distRule = "EQUAL_ACTIVE_MEMBERS";

        GroupIncome existing = dataService.getIncomesByGroupId(groupId).stream()
                    .filter(i -> (request.getReference() != null && !request.getReference().isBlank()
                            && request.getReference().equalsIgnoreCase(i.getReference()))
                            || ((request.getReference() == null || request.getReference().isBlank())
                            && request.getAmount().compareTo(i.getAmount()) == 0
                            && incomeDate.equals(i.getIncomeDate())
                            && request.getSource().equalsIgnoreCase(i.getSource())
                            && java.util.Objects.equals(request.getDescription(), i.getDescription())))
                    .findFirst().orElse(null);
        if (existing != null) {
            return ResponseEntity.ok(ApiResponse.ok("Income request already processed", existing));
        }

        GroupIncome income = new GroupIncome(
                groupId,
                request.getSource(),
                request.getDescription(),
                request.getAmount(),
                incomeDate,
                request.getPaymentMethod(),
                request.getReference(),
                distRule,
                username
        );
        GroupIncome saved = dataService.saveIncome(income);

        // Record in transaction ledger
        String txnId = "TXN-INC-" + UUID.randomUUID().toString().substring(0, 8);
        Transaction txn = new Transaction(
                txnId,
                groupId,
                null,
                null,
                null,
                TransactionType.OTHER_INCOME,
                saved.getAmount(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                saved.getIncomeDate(),
                saved.getReference(),
                "Other Income: " + saved.getSource() + " - " + saved.getDescription(),
                saved.getPaymentMethod(),
                username
        );
        txn.setCategory("OTHER_INCOME");
        dataService.saveTransaction(txn);

        auditService.log(
                groupId,
                userId,
                username,
                "INCOME_RECORDED",
                "GroupIncome",
                saved.getId(),
                null,
                "Amount: ₹" + saved.getAmount() + ", Source: " + saved.getSource(),
                "127.0.0.1"
        );

        // Every other-income record is distributed by the backend.
        saved = distributionService.distributeIncome(groupId, saved.getId(), new DistributionRequest(distRule), username);

        return ResponseEntity.ok(ApiResponse.ok("Other income recorded and distributed successfully", saved));
    }

    @PostMapping("/{incomeId}/distribute")
    public ResponseEntity<ApiResponse<GroupIncome>> distributeIncome(
            @PathVariable String groupId,
            @PathVariable String incomeId,
            @Valid @RequestBody DistributionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupSecurityService.validateGroupAccess(principal, groupId);
        String username = principal != null ? principal.getUsername() : "ADMIN";
        GroupIncome distributed = distributionService.distributeIncome(groupId, incomeId, request, username);
        return ResponseEntity.ok(ApiResponse.ok("Income successfully distributed among group members", distributed));
    }
}
