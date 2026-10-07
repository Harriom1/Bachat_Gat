package com.bachatgat.service;

import com.bachatgat.dto.DistributionRequest;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DistributionServiceTest {

    @Autowired
    private DistributionService distributionService;

    @Autowired
    private FirestoreDataService dataService;

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
    @DisplayName("Equal distribution of Other Expense deducts from each active member's balance")
    void testEqualExpenseDistribution() {
        String groupId = "bg-001";

        GroupExpense expense = new GroupExpense();
        expense.setGroupId(groupId);
        expense.setCategory("STATIONERY");
        expense.setDescription("Annual Registers & Audit Printing");
        expense.setAmount(new BigDecimal("2000.00"));
        expense.setExpenseDate(LocalDate.now());
        expense.setPaymentMode("CASH");
        expense.setRecordedBy("admin");
        GroupExpense savedExpense = dataService.saveExpense(expense);

        List<Member> activeMembers = dataService.getMembersByGroupId(groupId);
        int count = activeMembers.size();
        Member sampleMember = activeMembers.get(0);
        BigDecimal initialExpDist = sampleMember.getTotalOtherExpenseDistributed() != null ?
                sampleMember.getTotalOtherExpenseDistributed() : BigDecimal.ZERO;

        DistributionRequest distReq = new DistributionRequest();
        distReq.setDistributionRule("EQUAL");
        distReq.setNote("Shared administrative expense");

        GroupExpense result = distributionService.distributeExpense(groupId, savedExpense.getId(), distReq, "admin");
        assertTrue(result.isDistributed());

        BigDecimal expectedDeduction = new BigDecimal("2000.00").divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);
        Member updatedMember = dataService.getMemberById(sampleMember.getMemberId());
        assertEquals(initialExpDist.add(expectedDeduction), updatedMember.getTotalOtherExpenseDistributed());

        List<Transaction> transactions = dataService.getTransactionsByGroupId(groupId);
        boolean txFound = transactions.stream()
                .anyMatch(t -> t.getType() == TransactionType.MEMBER_EXPENSE_DISTRIBUTION &&
                        t.getMemberId().equals(sampleMember.getMemberId()) &&
                        t.getAmount().compareTo(expectedDeduction) == 0);
        assertTrue(txFound, "Member expense distribution transaction must be in financial ledger");
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
