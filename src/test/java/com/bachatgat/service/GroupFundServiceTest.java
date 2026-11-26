package com.bachatgat.service;

import com.bachatgat.model.Group;
import com.bachatgat.model.GroupExpense;
import com.bachatgat.model.GroupIncome;
import com.bachatgat.dto.DistributionRequest;
import com.bachatgat.model.Member;
import com.bachatgat.model.MemberStatus;
import com.bachatgat.model.Loan;
import com.bachatgat.model.LoanStatus;
import com.bachatgat.model.Transaction;
import com.bachatgat.model.TransactionType;
import com.bachatgat.repository.FirestoreDataService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GroupFundServiceTest {

    @Test
    void equalIncomeDistributionKeepsTheCashAndCreditsEachDashboardOnce() {
        FirestoreDataService data = new FirestoreDataService(null, null, null);
        Group group = new Group();
        group.setId("income-example");
        data.saveGroup(group);
        for (int i = 1; i <= 10; i++) {
            Member member = new Member();
            member.setMemberId("INCOME-" + i);
            member.setGroupId(group.getId());
            member.setTotalSavingsBalance(new BigDecimal("10000.00"));
            member.setCurrentShareBalance(new BigDecimal("10000.00"));
            data.saveMember(member);
        }
        LocalDate date = LocalDate.of(2026, 11, 25);
        Transaction bachat = new Transaction();
        bachat.setGroupId(group.getId());
        bachat.setType(TransactionType.SHARE_CONTRIBUTION);
        bachat.setAmount(new BigDecimal("100000.00"));
        bachat.setDate(date.minusDays(1));
        data.saveTransaction(bachat);
        GroupIncome income = data.saveIncome(new GroupIncome(group.getId(), "DONATION", "Group income",
                new BigDecimal("100000.00"), date, "BANK_TRANSFER", null, "EQUAL", "admin"));
        Transaction receipt = new Transaction();
        receipt.setGroupId(group.getId());
        receipt.setType(TransactionType.OTHER_INCOME);
        receipt.setAmount(income.getAmount());
        receipt.setDate(date);
        receipt.setReference(income.getId());
        receipt.setPaymentMethod("BANK_TRANSFER");
        data.saveTransaction(receipt);

        GroupFundService funds = new GroupFundService(data);
        DashboardService dashboard = new DashboardService(data, funds, mock(CollectionService.class), mock(MemberService.class));
        var beforeAllocation = dashboard.getAdminDashboard(group.getId());
        assertEquals(new BigDecimal("100000.00"), beforeAllocation.getTotalSavings());
        assertEquals(new BigDecimal("200000.00"), beforeAllocation.getGroupAvailableFund());
        assertEquals(new BigDecimal("200000.00"), funds.getAvailableFund(group.getId()));
        new DistributionService(data, new NotificationService(data), new AuditService(data))
                .distributeIncome(group.getId(), income.getId(), new DistributionRequest("EQUAL"), "admin");

        var fund = funds.getFundBreakdown(group.getId());
        assertEquals(new BigDecimal("100000.00"), fund.savings());
        assertEquals(new BigDecimal("100000.00"), fund.otherIncome());
        assertEquals(new BigDecimal("200000.00"), fund.availableFund());
        assertEquals(new BigDecimal("200000.00"), funds.getAvailableFundBefore(group.getId(), date.plusDays(1)));
        assertEquals(new BigDecimal("100000.00"), funds.getBalanceByPaymentMethod(group.getId(), true));

        var admin = dashboard.getAdminDashboard(group.getId());
        assertEquals(new BigDecimal("200000.00"), admin.getTotalSavings());
        assertEquals(new BigDecimal("100000.00"), admin.getTotalBachatCapital());
        assertEquals(new BigDecimal("100000.00"), admin.getTotalOtherIncome());
        assertEquals(new BigDecimal("200000.00"), admin.getGroupAvailableFund());
        for (int i = 1; i <= 10; i++) {
            var result = dashboard.getUserDashboard("INCOME-" + i);
            assertEquals(new BigDecimal("20000.00"), result.getMyTotalSavings());
            assertEquals(new BigDecimal("10000.00"), result.getMyBachatSavings());
            assertEquals(new BigDecimal("10000.00"), result.getMyIncome());
            assertEquals(new BigDecimal("200000.00"), result.getGroupRemainingFunds());
        }
        // Older allocations also wrote the income into the stored savings field.
        data.findMemberByMemberId("INCOME-1").orElseThrow().setTotalSavingsBalance(new BigDecimal("20000.00"));
        assertEquals(new BigDecimal("20000.00"), dashboard.getUserDashboard("INCOME-1").getMyTotalSavings());
        assertEquals(new BigDecimal("200000.00"), dashboard.getAdminDashboard(group.getId()).getTotalSavings());
        assertEquals(new BigDecimal("200000.00"), funds.getAvailableFund(group.getId()));
    }

    @Test
    void legacyDistributedIncomeCoexistsWithRecordedCashReceipts() {
        FirestoreDataService data = new FirestoreDataService(null, null, null);
        Group group = new Group();
        group.setId("mixed-income");
        data.saveGroup(group);
        LocalDate date = LocalDate.of(2026, 11, 25);
        for (String name : List.of("first", "second", "failed")) {
            GroupIncome income = new GroupIncome(group.getId(), "DONATION", name, new BigDecimal("1000"),
                    date, "CASH", null, "EQUAL", "admin");
            income.setDistributed(true);
            data.saveIncome(income);
            if ("second".equals(name)) continue;
            Transaction receipt = new Transaction();
            receipt.setGroupId(group.getId());
            receipt.setType(TransactionType.OTHER_INCOME);
            receipt.setAmount(income.getAmount());
            receipt.setDate(date);
            receipt.setReference(income.getId());
            receipt.setPaymentStatus("failed".equals(name) ? "FAILED" : "SUCCESS");
            data.saveTransaction(receipt);
        }
        GroupFundService funds = new GroupFundService(data);
        assertEquals(new BigDecimal("2000"), funds.getAvailableFund(group.getId()));
        assertEquals(new BigDecimal("2000"), funds.getAvailableFundBefore(group.getId(), date.plusDays(1)));
    }

    @Test
    void countsDistributedExpensesOnceWhenHistoricalRecordsAndLedgerCoexist() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        Group group = new Group();
        group.setId("group-expense-history");
        dataService.saveGroup(group);
        String groupId = group.getId();

        Member member = new Member();
        member.setMemberId("M-EXPENSE-HISTORY");
        member.setGroupId(groupId);
        member.setTotalSavingsBalance(new BigDecimal("800"));
        member.setTotalOtherExpenseDistributed(new BigDecimal("250"));
        member.setTotalExpenseDeductedFromSavings(new BigDecimal("200"));
        dataService.saveMember(member);

        LocalDate date = LocalDate.of(2026, 1, 15);
        GroupExpense oldExpense = new GroupExpense(groupId, "BANK_CHARGES", "Legacy fee",
                new BigDecimal("50"), date, "CASH", null, "admin");
        oldExpense.setDistributed(true);
        dataService.saveExpense(oldExpense);
        GroupExpense postedExpense = new GroupExpense(groupId, "STATIONERY", "New supplies",
                new BigDecimal("200"), date, "CASH", null, "admin");
        postedExpense.setDistributed(true);
        dataService.saveExpense(postedExpense);

        GroupFundService service = new GroupFundService(dataService);
        assertEquals(new BigDecimal("750"), service.getAvailableFund(groupId));

        Transaction cashDebit = new Transaction();
        cashDebit.setGroupId(groupId);
        cashDebit.setDate(date);
        cashDebit.setReference(postedExpense.getId());
        cashDebit.setType(TransactionType.OTHER_EXPENSE);
        cashDebit.setAmount(postedExpense.getAmount());
        dataService.saveTransaction(cashDebit);

        assertEquals(new BigDecimal("250"), service.getFundBreakdown(groupId).expenses());
        assertEquals(new BigDecimal("750"), service.getAvailableFund(groupId));
    }

    @Test
    void keepsLegacySavingsWhenExitAddsOnlyRefundLedgerEntry() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        String groupId = "group-legacy-mixed";

        Member active = new Member();
        active.setId("active");
        active.setMemberId("M-ACTIVE");
        active.setGroupId(groupId);
        active.setStatus(MemberStatus.ACTIVE);
        active.setTotalSavingsBalance(new BigDecimal("10000"));

        Member exited = new Member();
        exited.setId("exited");
        exited.setMemberId("M-EXITED");
        exited.setGroupId(groupId);
        exited.setStatus(MemberStatus.EXITED);
        exited.setTotalSavingsBalance(new BigDecimal("5000"));

        Transaction refund = new Transaction();
        refund.setGroupId(groupId);
        refund.setType(TransactionType.REFUND);
        refund.setAmount(new BigDecimal("5000"));

        dataService.saveMember(active);
        dataService.saveMember(exited);
        dataService.saveTransaction(refund);

        GroupFundService service = new GroupFundService(dataService);

        assertEquals(new BigDecimal("10000"), service.getFundBreakdown(groupId).availableFund());
        assertEquals(new BigDecimal("10000"), service.getFundBreakdown(groupId).savings());
    }

    @Test
    void memberExitClearsMemberBalanceDeductsGroupSavingsAndReservesRefund() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        String groupId = "group-exit-flow";

        var group = new com.bachatgat.model.Group();
        group.setId(groupId);
        group.setTotalSavingsBalance(new BigDecimal("15000"));
        dataService.saveGroup(group);

        Member member = new Member();
        member.setId("member-exit-flow");
        member.setMemberId("M-EXIT-FLOW");
        member.setGroupId(groupId);
        member.setFullName("Exit Test Member");
        member.setStatus(MemberStatus.ACTIVE);
        member.setTotalSavingsBalance(new BigDecimal("5000"));
        member.setCurrentShareBalance(new BigDecimal("5000"));
        dataService.saveMember(member);

        Member remainingMember = new Member();
        remainingMember.setId("member-remaining");
        remainingMember.setMemberId("M-REMAINING");
        remainingMember.setGroupId(groupId);
        remainingMember.setStatus(MemberStatus.ACTIVE);
        remainingMember.setTotalSavingsBalance(new BigDecimal("10000"));
        remainingMember.setCurrentShareBalance(new BigDecimal("10000"));
        dataService.saveMember(remainingMember);

        GroupFundService fundService = new GroupFundService(dataService);
        MemberExitService exitService = new MemberExitService(
                dataService, new TransactionService(dataService), new AuditService(dataService), fundService);

        var refund = exitService.exitMember(member.getId(), "admin", "Test exit");

        assertEquals(new BigDecimal("5000"), refund.getAmount());
        assertEquals("PENDING", refund.getStatus());
        assertEquals(MemberStatus.EXITED, dataService.getMemberById(member.getId()).getStatus());
        assertEquals(BigDecimal.ZERO, dataService.getMemberById(member.getId()).getTotalSavingsBalance());
        assertEquals(BigDecimal.ZERO, dataService.getMemberById(member.getId()).getCurrentShareBalance());
        assertEquals(new BigDecimal("10000"), dataService.findGroupById(groupId).orElseThrow().getTotalSavingsBalance());
        assertEquals(new BigDecimal("10000"), fundService.getAvailableFund(groupId));
    }

    @Test
    void preservesSignedFundBalanceInsteadOfHidingShortfallAsZero() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        String groupId = "group-signed-fund";

        Transaction savings = new Transaction();
        savings.setGroupId(groupId);
        savings.setType(TransactionType.SHARE_CONTRIBUTION);
        savings.setAmount(new BigDecimal("2000"));

        Transaction disbursement = new Transaction();
        disbursement.setGroupId(groupId);
        disbursement.setType(TransactionType.LOAN_DISBURSEMENT);
        disbursement.setAmount(new BigDecimal("5000"));

        dataService.saveTransaction(savings);
        dataService.saveTransaction(disbursement);

        GroupFundService service = new GroupFundService(dataService);

        assertEquals(new BigDecimal("-3000"), service.getAvailableFund(groupId));
    }

    @Test
    void keepsHistoricalSavingsWhenNewLedgerPaymentIsAdded() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        String groupId = "group-historical-savings";

        Member member = new Member();
        member.setId("historical-member");
        member.setMemberId("M-HISTORICAL");
        member.setGroupId(groupId);
        member.setStatus(MemberStatus.ACTIVE);
        member.setTotalSavingsBalance(new BigDecimal("192000"));
        dataService.saveMember(member);

        Transaction newPayment = new Transaction();
        newPayment.setGroupId(groupId);
        newPayment.setType(TransactionType.SHARE_CONTRIBUTION);
        newPayment.setAmount(new BigDecimal("2000"));
        dataService.saveTransaction(newPayment);

        GroupFundService service = new GroupFundService(dataService);

        assertEquals(new BigDecimal("192000"), service.getFundBreakdown(groupId).savings());
    }

    @Test
    void keepsVerifiedLedgerSavingsWhenMemberBalanceLagsBehind() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        String groupId = "group-current-payment";

        Member member = new Member();
        member.setId("current-member");
        member.setMemberId("M-CURRENT");
        member.setGroupId(groupId);
        member.setStatus(MemberStatus.ACTIVE);
        member.setTotalSavingsBalance(new BigDecimal("5600"));
        dataService.saveMember(member);

        Transaction firstPayment = new Transaction();
        firstPayment.setGroupId(groupId);
        firstPayment.setType(TransactionType.SHARE_CONTRIBUTION);
        firstPayment.setAmount(new BigDecimal("3300"));
        dataService.saveTransaction(firstPayment);

        Transaction secondPayment = new Transaction();
        secondPayment.setGroupId(groupId);
        secondPayment.setType(TransactionType.SHARE_CONTRIBUTION);
        secondPayment.setAmount(new BigDecimal("3300"));
        dataService.saveTransaction(secondPayment);

        GroupFundService service = new GroupFundService(dataService);

        assertEquals(new BigDecimal("6600"), service.getFundBreakdown(groupId).savings());
    }

    @Test
    void reconcilesDisbursedLoanRecordsMissingTheirLedgerEntry() {
        FirestoreDataService dataService = new FirestoreDataService(null, null, null);
        String groupId = "group-missing-disbursement-ledger";

        Transaction savings = new Transaction();
        savings.setGroupId(groupId);
        savings.setType(TransactionType.SHARE_CONTRIBUTION);
        savings.setAmount(new BigDecimal("100000"));
        dataService.saveTransaction(savings);

        Transaction ledgerLoan = new Transaction();
        ledgerLoan.setGroupId(groupId);
        ledgerLoan.setLoanId("LN-LEDGER");
        ledgerLoan.setType(TransactionType.LOAN_DISBURSEMENT);
        ledgerLoan.setAmount(new BigDecimal("60000"));
        dataService.saveTransaction(ledgerLoan);

        Loan legacyLoan = new Loan();
        legacyLoan.setLoanId("LN-LEGACY");
        legacyLoan.setGroupId(groupId);
        legacyLoan.setStatus(LoanStatus.ACTIVE);
        legacyLoan.setPrincipalAmount(new BigDecimal("9000"));
        dataService.saveLoan(legacyLoan);

        GroupFundService service = new GroupFundService(dataService);
        GroupFundService.FundBreakdown breakdown = service.getFundBreakdown(groupId);

        assertEquals(new BigDecimal("69000"), breakdown.loansDisbursed());
        assertEquals(new BigDecimal("31000"), breakdown.availableFund());
    }
}
