package com.bachatgat.service;

import com.bachatgat.controller.AdminExpenseController;
import com.bachatgat.dto.DistributionRequest;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DistributionServiceTest {

    @Autowired
    private DistributionService distributionService;

    @Autowired
    private FirestoreDataService dataService;

    @Autowired
    private AdminExpenseController expenseController;

    @Autowired
    private GroupFundService fundService;

    @Test
    @DisplayName("Equal distribution of Other Income adjusts each active member's share balance and creates ledger entries")
    void testEqualIncomeDistribution() {
        String groupId = "bg-001";

        GroupIncome income = new GroupIncome();
        income.setGroupId(groupId);
        income.setSource("DONATION");
        income.setDescription("CSR Grant from State Bank");
        income.setAmount(new BigDecimal("10000.00"));
        income.setIncomeDate(LocalDate.now());
        income.setPaymentMethod("BANK_TRANSFER");
        income.setRecordedBy("admin");
        GroupIncome savedIncome = dataService.saveIncome(income);

        List<Member> activeMembers = dataService.getMembersByGroupId(groupId);
        assertFalse(activeMembers.isEmpty());
        int count = activeMembers.size();

        Member sampleMember = activeMembers.get(0);
        BigDecimal initialIncomeDist = sampleMember.getTotalOtherIncomeDistributed() != null ?
                sampleMember.getTotalOtherIncomeDistributed() : BigDecimal.ZERO;

        DistributionRequest distReq = new DistributionRequest();
        distReq.setDistributionRule("EQUAL");
        distReq.setNote("Annual CSR distribution");

        GroupIncome result = distributionService.distributeIncome(groupId, savedIncome.getId(), distReq, "admin");
        assertTrue(result.isDistributed());
        assertEquals("EQUAL", result.getDistributionRule());

        // Expected per member = 10000 / count
        BigDecimal expectedPerMember = new BigDecimal("10000.00").divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);
        Member updatedMember = dataService.getMemberById(sampleMember.getMemberId());
        assertEquals(initialIncomeDist.add(expectedPerMember), updatedMember.getTotalOtherIncomeDistributed());

        // Check ledger transactions
        List<Transaction> transactions = dataService.getTransactionsByGroupId(groupId);
        boolean txFound = transactions.stream()
                .anyMatch(t -> t.getType() == TransactionType.MEMBER_INCOME_DISTRIBUTION &&
                        t.getMemberId().equals(sampleMember.getMemberId()) &&
                        t.getAmount().compareTo(expectedPerMember) == 0);
        assertTrue(txFound, "Member income distribution transaction must be in financial ledger");
    }

    @Test
    @DisplayName("Recording an expense reduces member savings and group cash exactly once")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testEqualExpenseDistribution() {
        String groupId = "bg-001";
        var principal = new UserPrincipal("usr-admin-01", "admin", "", Role.ADMIN, groupId, null);
        BigDecimal amount = new BigDecimal("2000.01");
        LocalDate expenseDate = LocalDate.now();
        GroupExpense expense = new GroupExpense(groupId, "STATIONERY", "Annual registers",
                amount, expenseDate, "CASH", "Stationery supplier", "admin");
        expense.setReference("STATIONERY-REGRESSION");
        BigDecimal previousFund = fundService.getAvailableFund(groupId);
        BigDecimal previousExpenses = fundService.getFundBreakdown(groupId).expenses();
        BigDecimal previousOpening = fundService.getAvailableFundBefore(groupId, expenseDate.plusDays(1));
        Map<String, BigDecimal> previousSavings = dataService.getMembersByGroupId(groupId).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .collect(Collectors.toMap(Member::getMemberId, Member::getTotalSavingsBalance));
        assertFalse(previousSavings.isEmpty());
        GroupExpense result = expenseController.createExpense(groupId, expense, principal).getBody().getData();
        assertTrue(result.isDistributed());
        GroupExpense retry = expenseController.createExpense(groupId, expense, principal).getBody().getData();
        assertEquals(result.getId(), retry.getId());

        List<Transaction> transactions = dataService.getTransactionsByGroupId(groupId);
        Map<String, BigDecimal> allocations = transactions.stream()
                .filter(t -> t.getType() == TransactionType.MEMBER_EXPENSE_DISTRIBUTION
                        && result.getId().equals(t.getReference()))
                .collect(Collectors.toMap(Transaction::getMemberId, Transaction::getAmount));
        assertEquals(previousSavings.keySet(), allocations.keySet());
        assertEquals(amount, allocations.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal baseShare = amount.divide(BigDecimal.valueOf(allocations.size()), 2, RoundingMode.DOWN);
        BigDecimal remainder = amount.subtract(baseShare.multiply(BigDecimal.valueOf(allocations.size())));
        BigDecimal largestShare = baseShare.add(remainder);
        allocations.forEach((memberId, share) -> {
            assertTrue(share.equals(baseShare) || share.equals(largestShare));
            assertEquals(0, previousSavings.get(memberId).subtract(share)
                    .compareTo(dataService.getMemberById(memberId).getTotalSavingsBalance()));
        });
        assertEquals(1, transactions.stream().filter(t -> t.getType() == TransactionType.OTHER_EXPENSE
                && result.getId().equals(t.getReference())).count());
        assertEquals(0, previousFund.subtract(amount).compareTo(fundService.getAvailableFund(groupId)));
        assertEquals(0, previousExpenses.add(amount).compareTo(fundService.getFundBreakdown(groupId).expenses()));
        assertEquals(0, previousOpening.subtract(amount)
                .compareTo(fundService.getAvailableFundBefore(groupId, expenseDate.plusDays(1))));
    }

    @Test
    @DisplayName("Section 6, 7, 8, 9 & 50: Loan interest ₹200 distributed among all members including borrower")
    void testLoanInterestDistributedToAllMembersIncludingBorrower() {
        String groupId = "bg-001";
        List<Member> activeMembers = dataService.getMembersByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .toList();
        assertFalse(activeMembers.isEmpty());
        int count = activeMembers.size();

        // Suppose borrower is Member 0
        Member borrower = activeMembers.get(0);
        BigDecimal borrowerInitialLoanInt = borrower.getTotalLoanInterestReceived() != null ?
                borrower.getTotalLoanInterestReceived() : BigDecimal.ZERO;

        BigDecimal interestAmount = new BigDecimal("200.00");
        String loanId = "LN-2026-TEST";

        GroupIncome distributedIncome = distributionService.distributeLoanInterest(
                groupId, loanId, borrower.getMemberId(), interestAmount, LocalDate.now(), "admin"
        );

        assertNotNull(distributedIncome);
        assertEquals("LOAN_INTEREST", distributedIncome.getCategory());
        assertEquals(interestAmount, distributedIncome.getDistributedAmount());

        // Verify Borrower (Member 0) received their share!
        BigDecimal expectedPerMember = interestAmount.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.DOWN);
        BigDecimal remainder = interestAmount.subtract(expectedPerMember.multiply(BigDecimal.valueOf(count)));
        BigDecimal expectedBorrowerShare = expectedPerMember.add(remainder); // Member 0 gets remainder cent if any

        Member updatedBorrower = dataService.getMemberById(borrower.getMemberId());
        assertEquals(borrowerInitialLoanInt.add(expectedBorrowerShare), updatedBorrower.getTotalLoanInterestReceived(),
                "Borrower must NOT be excluded from interest income dividend distribution!");

        // Verify all other active members received their share as well
        for (int i = 1; i < count; i++) {
            Member m = activeMembers.get(i);
            Member updatedM = dataService.getMemberById(m.getMemberId());
            assertNotNull(updatedM.getTotalLoanInterestReceived());
            assertTrue(updatedM.getTotalLoanInterestReceived().compareTo(BigDecimal.ZERO) > 0);
        }

        // Verify ledger transactions for MEMBER_INCOME_DISTRIBUTION
        List<Transaction> transactions = dataService.getTransactionsByGroupId(groupId);
        long distTxnCount = transactions.stream()
                .filter(t -> t.getType() == TransactionType.MEMBER_INCOME_DISTRIBUTION &&
                        loanId.equals(t.getLoanId()))
                .count();
        assertEquals(count, distTxnCount, "Ledger must contain income distribution transaction for every member");
    }
}
