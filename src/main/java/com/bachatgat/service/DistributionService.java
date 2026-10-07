package com.bachatgat.service;

import com.bachatgat.dto.DistributionRequest;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class DistributionService {

    private final FirestoreDataService dataService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public DistributionService(FirestoreDataService dataService,
                               NotificationService notificationService,
                               AuditService auditService) {
        this.dataService = dataService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /**
     * Distributes loan interest income among all active members of the BachatGat (including the borrower).
     * Requirements 6, 7, 8, 9, 30 & 50.
     * Guaranteed:
     * 1. Interest is recorded as Group Income under category 'LOAN_INTEREST'.
     * 2. Allocated to all active members including the borrower.
     * 3. Exact rupee balance maintained with decimal rounding handled safely without creating or losing money.
     * 4. Double-entry ledger entries created for each member's dividend.
     */
    public GroupIncome distributeLoanInterest(String groupId, String loanId, String borrowerMemberId,
                                              BigDecimal interestAmount, LocalDate paymentDate, String recordedBy) {
        if (interestAmount == null || interestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        LocalDate payDate = paymentDate != null ? paymentDate : LocalDate.now();

        // 1. Create GroupIncome record
        GroupIncome income = new GroupIncome();
        income.setGroupId(groupId);
        income.setCategory("LOAN_INTEREST");
        income.setSource("LOAN_REPAYMENT_INTEREST");
        income.setDescription("Loan Interest Income received from loan " + loanId + " (Borrower: " + borrowerMemberId + ")");
        income.setAmount(interestAmount);
        income.setIncomeDate(payDate);
        income.setPaymentMethod("INTERNAL_ALLOCATION");
        income.setDistributionRule("EQUAL_ACTIVE_MEMBERS");
        income.setDistributed(true);
        income.setDistributedAmount(interestAmount);
        income.setRecordedBy(recordedBy);
        GroupIncome savedIncome = dataService.saveIncome(income);

        // 2. Fetch all active members of the group
        List<Member> activeMembers = dataService.getMembersByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .toList();

        if (activeMembers.isEmpty()) {
            return savedIncome;
        }

        int count = activeMembers.size();
        BigDecimal countBD = BigDecimal.valueOf(count);
        BigDecimal perMember = interestAmount.divide(countBD, 2, RoundingMode.DOWN);
        BigDecimal totalAllocated = perMember.multiply(countBD);
        BigDecimal remainder = interestAmount.subtract(totalAllocated);

        // 3. Allocate to all active members (including the borrower)
        for (int i = 0; i < count; i++) {
            Member member = activeMembers.get(i);
            // Remainder cents allocated to first member to keep total exact to the rupee
            BigDecimal share = (i == 0) ? perMember.add(remainder) : perMember;

            if (share.compareTo(BigDecimal.ZERO) > 0) {
                // Update member's income balance and credit directly to total savings balance
                BigDecimal prevInterestReceived = member.getTotalLoanInterestReceived() != null
                        ? member.getTotalLoanInterestReceived() : BigDecimal.ZERO;
                member.setTotalLoanInterestReceived(prevInterestReceived.add(share));

                BigDecimal prevSavings = member.getTotalSavingsBalance() != null ? member.getTotalSavingsBalance() : BigDecimal.ZERO;
                member.setTotalSavingsBalance(prevSavings.add(share));

                BigDecimal prevShare = member.getCurrentShareBalance() != null ? member.getCurrentShareBalance() : BigDecimal.ZERO;
                member.setCurrentShareBalance(prevShare.add(share));

                dataService.saveMember(member);

                // Create ledger entry
                Transaction txn = new Transaction(
                        "TXN-" + System.currentTimeMillis() + "-LINT-" + (i + 1),
                        groupId, member.getMemberId(), member.getFullName(), loanId,
                        TransactionType.MEMBER_INCOME_DISTRIBUTION, share, BigDecimal.ZERO, BigDecimal.ZERO,
                        payDate, savedIncome.getId(),
                        "Loan Interest Dividend Allocation (Loan: " + loanId + ", Rate share: ₹" + share + ")",
                        "INTERNAL_ALLOCATION", recordedBy
                );
                txn.setCategory("DISTRIBUTION");
                dataService.saveTransaction(txn);

                // Notify member of income allocation
                dataService.findUserByMemberId(member.getMemberId()).ifPresent(u ->
                        notificationService.sendNotification(groupId, u.getId(), "USER",
                                "Interest Income Dividend Received",
                                "₹" + share + " credited to your income balance as group interest dividend from Loan " + loanId + ".",
                                "INTEREST_DIVIDEND")
                );
            }
        }

        auditService.log(groupId, recordedBy, recordedBy, "LOAN_INTEREST_DISTRIBUTED", "INCOME",
                savedIncome.getId(), null,
                "Distributed ₹" + interestAmount + " interest income among " + count + " active members (including borrower)",
                "127.0.0.1");

        // Synchronize Group total savings
        dataService.findGroupById(groupId).ifPresent(grp -> {
            BigDecimal grpSavings = grp.getTotalSavings() != null ? grp.getTotalSavings() : BigDecimal.ZERO;
            grp.setTotalSavings(grpSavings.add(interestAmount));
            dataService.saveGroup(grp);
        });

        return savedIncome;
    }

    /**
     * Distributes Other Group Income across members and adjusts member share balances accordingly.
     * Section 16 & 17
     */
    public synchronized GroupIncome distributeIncome(String groupId, String incomeId, DistributionRequest request, String adminUsername) {
        GroupIncome income = dataService.findIncomeById(incomeId)
                .orElseThrow(() -> new ResourceNotFoundException("Income record not found with ID: " + incomeId));

        if (!groupId.equals(income.getGroupId())) {
            throw new InvalidFinancialOperationException("Income record does not belong to the selected group.");
        }

        if (income.isDistributed()) {
            throw new InvalidFinancialOperationException("Income record '" + incomeId + "' has already been distributed to members.");
        }

        // Other income is always shared equally. Ignore any client-supplied
        // distribution rule, including legacy GROUP_LEVEL_ONLY requests.
        String rule = "EQUAL";
        income.setDistributionRule(rule);

        List<Member> activeMembers = dataService.getMembersByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .toList();

        if (activeMembers.isEmpty()) {
            throw new InvalidFinancialOperationException("Cannot distribute income: No active members found in group.");
        }

        Map<Member, BigDecimal> allocations = calculateAllocations(income.getAmount(), rule, activeMembers, request);

        // Apply allocations to member share balances
        for (Map.Entry<Member, BigDecimal> entry : allocations.entrySet()) {
            Member member = entry.getKey();
            BigDecimal amount = entry.getValue();

            BigDecimal prevShare = member.getCurrentShareBalance() != null ? member.getCurrentShareBalance() : BigDecimal.ZERO;
            BigDecimal newShare = prevShare.add(amount);
            member.setCurrentShareBalance(newShare);

            BigDecimal prevSavings = member.getTotalSavingsBalance() != null ? member.getTotalSavingsBalance() : BigDecimal.ZERO;
            member.setTotalSavingsBalance(prevSavings.add(amount));

            BigDecimal previousIncome = member.getTotalOtherIncomeDistributed() != null
                    ? member.getTotalOtherIncomeDistributed() : BigDecimal.ZERO;
            member.setTotalOtherIncomeDistributed(previousIncome.add(amount));
            dataService.saveMember(member);

            // Create Member Adjustment record
            MemberAdjustment adjustment = new MemberAdjustment(
                    groupId, member.getMemberId(), member.getFullName(),
                    "OTHER_INCOME", income.getId(),
                    "Distribution of " + income.getSource() + " (" + income.getDescription() + ")",
                    amount, prevShare, newShare, income.getIncomeDate(), adminUsername
            );
            dataService.saveAdjustment(adjustment);

            // Record in Transaction Ledger
            Transaction txn = new Transaction(
                    "TXN-INC-DIST-" + UUID.randomUUID().toString().substring(0, 12),
                    groupId, member.getMemberId(), member.getFullName(), null,
                    TransactionType.MEMBER_INCOME_DISTRIBUTION, amount, BigDecimal.ZERO, BigDecimal.ZERO,
                    income.getIncomeDate(), income.getId(),
                    "Share Balance Credit: Income Distribution (" + rule + ")",
                    "INTERNAL_TRANSFER", adminUsername
            );
            txn.setCategory("DISTRIBUTION");
            dataService.saveTransaction(txn);

            // Notify member
            dataService.findUserByMemberId(member.getMemberId()).ifPresent(u ->
                    notificationService.sendNotification(groupId, u.getId(), "USER",
                            "Income Distribution Credited",
                            "₹" + amount + " has been added to your share balance from " + income.getSource() + ".",
                            "INCOME_CREDIT")
            );
        }

        income.setDistributed(true);
        income.setDistributedAmount(income.getAmount());
        dataService.saveIncome(income);

        auditService.log(groupId, adminUsername, adminUsername, "INCOME_DISTRIBUTED", "INCOME",
                incomeId, null, "Distributed ₹" + income.getAmount() + " to " + allocations.size() +
                        " members using rule: " + rule, "127.0.0.1");

        // Synchronize Group total savings
        dataService.findGroupById(groupId).ifPresent(grp -> {
            BigDecimal grpSavings = grp.getTotalSavings() != null ? grp.getTotalSavings() : BigDecimal.ZERO;
            grp.setTotalSavings(grpSavings.add(income.getAmount()));
            dataService.saveGroup(grp);
        });

        return income;
    }

    /**
     * Distributes Other Group Expense across members and deducts from member share balances accordingly.
     * Section 15 & 16
     */
    public synchronized GroupExpense distributeExpense(String groupId, String expenseId, DistributionRequest request, String adminUsername) {
        GroupExpense expense = dataService.getExpensesByGroupId(groupId).stream()
                .filter(e -> e.getId().equalsIgnoreCase(expenseId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Expense record not found with ID: " + expenseId));

        if (expense.isDistributed()) {
            throw new InvalidFinancialOperationException("Expense record '" + expenseId + "' has already been distributed to members.");
        }

        // Other expenses are always allocated equally. Ignore any
        // client-supplied distribution rule.
        String rule = "EQUAL";
        expense.setDistributionRule(rule);

        List<Member> activeMembers = dataService.getMembersByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .toList();

        if (activeMembers.isEmpty()) {
            throw new InvalidFinancialOperationException("Cannot distribute expense: No active members found in group.");
        }

        Map<Member, BigDecimal> deductions = calculateAllocations(expense.getAmount(), rule, activeMembers, request);

        // Apply deductions to member share balances
        for (Map.Entry<Member, BigDecimal> entry : deductions.entrySet()) {
            Member member = entry.getKey();
            BigDecimal amount = entry.getValue();

            BigDecimal prevShare = member.getCurrentShareBalance() != null ? member.getCurrentShareBalance() : BigDecimal.ZERO;
            BigDecimal newShare = prevShare.subtract(amount); // Deduct share
            member.setCurrentShareBalance(newShare);

            BigDecimal prevSavings = member.getTotalSavingsBalance() != null ? member.getTotalSavingsBalance() : BigDecimal.ZERO;
            member.setTotalSavingsBalance(prevSavings.subtract(amount).max(BigDecimal.ZERO));

            BigDecimal previousExpense = member.getTotalOtherExpenseDistributed() != null
                    ? member.getTotalOtherExpenseDistributed() : BigDecimal.ZERO;
            member.setTotalOtherExpenseDistributed(previousExpense.add(amount));
            dataService.saveMember(member);

            // Create Member Adjustment record
            MemberAdjustment adjustment = new MemberAdjustment(
                    groupId, member.getMemberId(), member.getFullName(),
                    "OTHER_EXPENSE", expense.getId(),
                    "Deduction for " + expense.getCategory() + " (" + expense.getDescription() + ")",
                    amount.negate(), prevShare, newShare, expense.getExpenseDate(), adminUsername
            );
            dataService.saveAdjustment(adjustment);

            // Record in Transaction Ledger
            Transaction txn = new Transaction(
                    "TXN-EXP-DIST-" + UUID.randomUUID().toString().substring(0, 12),
                    groupId, member.getMemberId(), member.getFullName(), null,
                    TransactionType.MEMBER_EXPENSE_DISTRIBUTION, amount, BigDecimal.ZERO, BigDecimal.ZERO,
                    expense.getExpenseDate(), expense.getId(),
                    "Share Balance Deduction: Expense Distribution (" + rule + ")",
                    "INTERNAL_TRANSFER", adminUsername
            );
            txn.setCategory("DISTRIBUTION");
            dataService.saveTransaction(txn);

            // Notify member
            dataService.findUserByMemberId(member.getMemberId()).ifPresent(u ->
                    notificationService.sendNotification(groupId, u.getId(), "USER",
                            "Expense Allocation Notice",
                            "₹" + amount + " allocated from your share balance for group expense (" + expense.getCategory() + ").",
                            "EXPENSE_DEBIT")
            );
        }

        expense.setDistributed(true);
        expense.setDistributedAmount(expense.getAmount());
        dataService.saveExpense(expense);

        auditService.log(groupId, adminUsername, adminUsername, "EXPENSE_DISTRIBUTED", "EXPENSE",
                expenseId, null, "Distributed expense ₹" + expense.getAmount() + " to " + deductions.size() +
                        " members using rule: " + rule, "127.0.0.1");

        // Synchronize Group total savings
        dataService.findGroupById(groupId).ifPresent(grp -> {
            BigDecimal grpSavings = grp.getTotalSavings() != null ? grp.getTotalSavings() : BigDecimal.ZERO;
            grp.setTotalSavings(grpSavings.subtract(expense.getAmount()).max(BigDecimal.ZERO));
            dataService.saveGroup(grp);
        });

        return expense;
    }

    private Map<Member, BigDecimal> calculateAllocations(BigDecimal totalAmount, String rule, List<Member> activeMembers, DistributionRequest request) {
        Map<Member, BigDecimal> result = new LinkedHashMap<>();

        if ("EQUAL_ACTIVE_MEMBERS".equalsIgnoreCase(rule) || "EQUAL".equalsIgnoreCase(rule)) {
            BigDecimal count = BigDecimal.valueOf(activeMembers.size());
            BigDecimal perMember = totalAmount.divide(count, 2, RoundingMode.DOWN);
            BigDecimal distributed = perMember.multiply(count);
            BigDecimal remainder = totalAmount.subtract(distributed);

            for (int i = 0; i < activeMembers.size(); i++) {
                Member m = activeMembers.get(i);
                // Allocate remainder to first member to keep total exact to the rupee
                BigDecimal share = (i == 0) ? perMember.add(remainder) : perMember;
                result.put(m, share);
            }
        } else if ("PROPORTIONAL_SAVINGS".equalsIgnoreCase(rule) || "PROPORTIONAL".equalsIgnoreCase(rule)) {
            BigDecimal totalGroupSavings = activeMembers.stream()
                    .map(m -> m.getCurrentShareBalance() != null ? m.getCurrentShareBalance() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalGroupSavings.compareTo(BigDecimal.ZERO) <= 0) {
                // Fallback to equal if savings balance is zero
                return calculateAllocations(totalAmount, "EQUAL_ACTIVE_MEMBERS", activeMembers, request);
            }

            BigDecimal runningSum = BigDecimal.ZERO;
            for (int i = 0; i < activeMembers.size(); i++) {
                Member m = activeMembers.get(i);
                BigDecimal memberShare = m.getCurrentShareBalance() != null ? m.getCurrentShareBalance() : BigDecimal.ZERO;
                if (i == activeMembers.size() - 1) {
                    // Last member gets remaining difference so total matches exact
                    result.put(m, totalAmount.subtract(runningSum));
                } else {
                    BigDecimal prop = totalAmount.multiply(memberShare).divide(totalGroupSavings, 2, RoundingMode.HALF_EVEN);
                    result.put(m, prop);
                    runningSum = runningSum.add(prop);
                }
            }
        } else if ("SELECTED_MEMBERS".equalsIgnoreCase(rule)) {
            List<String> targetIds = request.getTargetMemberIds() != null ? request.getTargetMemberIds() : List.of();
            List<Member> selected = activeMembers.stream()
                    .filter(m -> targetIds.contains(m.getMemberId()) || targetIds.contains(m.getId()))
                    .toList();
            if (selected.isEmpty()) {
                throw new InvalidFinancialOperationException("No valid members selected for distribution.");
            }
            BigDecimal count = BigDecimal.valueOf(selected.size());
            BigDecimal perMember = totalAmount.divide(count, 2, RoundingMode.DOWN);
            BigDecimal remainder = totalAmount.subtract(perMember.multiply(count));
            for (int i = 0; i < selected.size(); i++) {
                Member m = selected.get(i);
                result.put(m, (i == 0) ? perMember.add(remainder) : perMember);
            }
        } else if ("MANUAL".equalsIgnoreCase(rule)) {
            Map<String, BigDecimal> manualMap = request.getManualAmounts() != null ? request.getManualAmounts() : Map.of();
            BigDecimal sum = manualMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(totalAmount) != 0) {
                throw new InvalidFinancialOperationException("Manual distribution sum (₹" + sum + ") must exactly equal total amount (₹" + totalAmount + ")");
            }
            for (Member m : activeMembers) {
                if (manualMap.containsKey(m.getMemberId())) {
                    result.put(m, manualMap.get(m.getMemberId()));
                }
            }
        } else {
            throw new InvalidFinancialOperationException("Unsupported distribution rule: " + rule);
        }

        return result;
    }
}
