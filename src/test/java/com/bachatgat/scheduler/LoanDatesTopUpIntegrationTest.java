package com.bachatgat.scheduler;

import com.bachatgat.dto.LoanApplicationRequest;
import com.bachatgat.dto.LoanApprovalRequest;
import com.bachatgat.dto.LoanExtraPaymentRequest;
import com.bachatgat.dto.LoanRepaymentRequest;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import com.bachatgat.repository.LoanScheduleCalculatorSupport;
import com.bachatgat.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoanDatesTopUpIntegrationTest {
    private final FirestoreDataService data = new FirestoreDataService(null, null, new ObjectMapper().findAndRegisterModules());
    private final AuditService audit = new AuditService(data);
    private final TransactionService transactions = new TransactionService(data);
    private final NotificationService notifications = new NotificationService(data);
    private final GroupFundService funds = new GroupFundService(data);
    private final LoanApplicationService applications = new LoanApplicationService(data, transactions, notifications, audit, funds);
    private final LoanService loans = new LoanService(data, transactions, notifications, audit, null, null);
    private final ScheduledJobsService scheduler = new ScheduledJobsService(data, null, notifications);
    private final LocalDate today = LoanScheduleCalculatorSupport.today();
    private Group group;
    private Member member;

    @BeforeEach
    void setUp() {
        group = group("Bachatgat Mitri Sankpla");
        member = member("MEM-001", group);
    }

    @Test
    void groupDefaultsAndManualDatesAreScopedAndPersistAcrossScheduleRebuilds() {
        Group other = group("Other Bachat Gat");
        other.setLoanDueDay(18);
        other.setLoanLatePenalty(new BigDecimal("25.50"));
        data.saveGroup(other);
        group.setDefaultLoanStartDate(LocalDate.of(2026, 12, 1));
        group.setDefaultFirstEmiDueDate(LocalDate.of(2026, 12, 10));
        data.saveGroup(group);
        LoanApplication app = apply(member, 12_000, null);
        assertEquals("2026-12-01", applications.getApprovalDetails(app).get("defaultLoanStartDate"));
        assertEquals("2026-12-10", applications.getApprovalDetails(app).get("defaultFirstEmiDueDate"));
        assertEquals(10, applications.getApprovalDetails(app).get("dueDay"));
        assertEquals(new BigDecimal("100"), applications.getApprovalDetails(app).get("penalty"));
        LoanApplication otherApp = apply(member("MEM-002", other), 5_000, null);
        assertEquals(18, applications.getApprovalDetails(otherApp).get("dueDay"));
        assertFalse(applications.getApprovalDetails(otherApp).containsKey("defaultLoanStartDate"));
        assertEquals(new BigDecimal("25.50"), applications.getApprovalDetails(otherApp).get("penalty"));

        var request = terms(12_000, 3, today.minusDays(2), today.plusDays(12), "100");
        request.setFirstEmiDueDate(null);
        assertThrows(InvalidFinancialOperationException.class,
                () -> applications.approveApplication(app.getId(), request, "admin"));
        request.setFirstEmiDueDate(today.plusDays(12));
        Loan loan = applications.approveApplication(app.getId(), request, "admin");
        assertEquals(today.minusDays(2), loan.getStartDate());
        assertEquals(today.plusDays(12), loan.getNextDueDate());
        var rows = loans.getLoanSchedule(loan.getId());
        assertEquals(today.plusDays(12).plusMonths(1), rows.get(1).getDueDate());
        assertEquals(0, rows.getFirst().getInterestAmount().compareTo(new BigDecimal("120")));
    }

    @Test
    void configuredMonthEndDueDayRecoversInLongerMonths() {
        LocalDate firstDue = LocalDate.of(today.getYear() + 1, 2, 1);
        firstDue = firstDue.withDayOfMonth(firstDue.lengthOfMonth());
        LoanApplication app = apply(member, 12_000, null);
        LoanApprovalRequest request = terms(12_000, 3, today, firstDue, "100");
        request.setEmiDueDay(31);
        Loan loan = applications.approveApplication(app.getId(), request, "admin");
        var rows = loans.getLoanSchedule(loan.getId());
        assertEquals(firstDue, rows.getFirst().getDueDate());
        assertEquals(LocalDate.of(firstDue.getYear(), 3, 31), rows.get(1).getDueDate());
        assertEquals(LocalDate.of(firstDue.getYear(), 4, 30), rows.getLast().getDueDate());
    }

    @Test
    void penaltyAccruesOnceAfterDueDateAndPaymentClosesLoanWithSeparateCashLedger() {
        LoanApplication app = apply(member, 12_000, null);
        LocalDate due = today.minusDays(1);
        Loan loan = applications.approveApplication(app.getId(), terms(12_000, 1, due.minusMonths(1), due, "100"), "admin");
        BigDecimal scheduled = loan.getTotalOutstanding();
        scheduler.runLoanDueCheck(due);
        assertMoney("0", data.findLoanById(loan.getId()).orElseThrow().getOutstandingPenalty());
        scheduler.runLoanDueCheck(today);
        scheduler.runLoanDueCheck(today);
        Loan overdue = loans.getLoanById(loan.getId());
        assertEquals(LoanStatus.OVERDUE, overdue.getStatus());
        assertEquals(1, overdue.getOverdueInstallments());
        assertMoney("100", overdue.getOutstandingPenalty());
        assertEquals(0, scheduled.add(new BigDecimal("100")).compareTo(overdue.getTotalOutstanding()));
        assertEquals(0, overdue.getTotalOutstanding().compareTo(data.findGroupById(group.getId()).orElseThrow().getTotalLoanOutstanding()));
        var row = loans.getLoanSchedule(loan.getId()).getFirst();
        assertMoney("100", row.getPenaltyAmount());

        Loan repaid = loans.recordRepayment(loan.getId(),
                new LoanRepaymentRequest(overdue.getTotalOutstanding(), today, "BANK_TRANSFER", "PAY-1"), "admin");
        assertEquals(LoanStatus.COMPLETED, repaid.getStatus());
        assertMoney("0", repaid.getTotalOutstanding());
        assertMoney("0", repaid.getOutstandingPenalty());
        assertNull(repaid.getNextDueDate());
        assertEquals(RepaymentStatus.PAID, loans.getLoanSchedule(loan.getId()).getFirst().getStatus());
        assertMoney("100", loans.getLoanSchedule(loan.getId()).getFirst().getPaidPenaltyAmount());
        assertMoney("100", entries(TransactionType.LOAN_PENALTY_INCOME).getFirst().getAmount());
        assertEquals(0, scheduled.compareTo(entries(TransactionType.LOAN_REPAYMENT).getFirst().getAmount()));
        assertMoney("0", data.findMemberByMemberId(member.getMemberId()).orElseThrow().getCurrentLoanOutstanding());
    }

    @Test
    void backdatedOnTimeReceiptClearsAnUnpaidLateCharge() {
        LocalDate due = today.minusDays(2);
        LoanApplication app = apply(member, 12_000, null);
        Loan loan = applications.approveApplication(app.getId(), terms(12_000, 1, due.minusMonths(1), due, "100"), "admin");
        scheduler.runLoanDueCheck(today);
        assertMoney("100", loans.getLoanById(loan.getId()).getOutstandingPenalty());
        Loan repaid = loans.recordRepayment(loan.getId(), new LoanRepaymentRequest(
                loan.getMonthlyInstallment(), due, "BANK_TRANSFER", "ON-TIME"), "admin");
        assertEquals(LoanStatus.COMPLETED, repaid.getStatus());
        assertMoney("0", repaid.getOutstandingPenalty());
        assertTrue(entries(TransactionType.LOAN_PENALTY_INCOME).isEmpty());
    }

    @Test
    void topUpReplacesPrincipalAndKeepsAdditionalCashAndOriginalHistorySeparate() {
        LoanApplication originalApp = apply(member, 280_000, null);
        Loan original = applications.approveApplication(originalApp.getId(),
                terms(280_000, 18, today.minusDays(2), today.plusDays(20), "100"), "admin");
        LoanExtraPaymentRequest extra = new LoanExtraPaymentRequest();
        extra.setAmount(BigDecimal.valueOf(10_000));
        extra.setPaymentDate(today.minusDays(1));
        extra.setReferenceNumber("PRE-TOPUP-PRINCIPAL");
        Loan paid = loans.recordExtraPayment(original.getId(), extra, "admin");
        assertMoney("270000", paid.getOutstandingPrincipal());
        Member someoneElse = member("MEM-003", group("Other Group"));
        assertThrows(InvalidFinancialOperationException.class, () -> apply(someoneElse, 5_000, original.getId()));
        assertThrows(InvalidFinancialOperationException.class, () -> apply(member, 10_000, null));

        LoanApplication app = apply(member, 100_000, original.getId());
        LocalDate futureStart = today.plusMonths(1);
        LocalDate firstEmi = futureStart.plusMonths(1);
        var request = terms(100_000, 12, futureStart, firstEmi, "100");
        request.setExpectedOutstandingPrincipal(new BigDecimal("269999"));
        assertThrows(InvalidFinancialOperationException.class,
                () -> applications.approveApplication(app.getId(), request, "admin"));
        request.setExpectedOutstandingPrincipal(new BigDecimal("270000"));
        var quote = applications.previewApproval(app, request);
        BigDecimal beforeCash = funds.getAvailableFund(group.getId());
        Loan replacement = applications.approveApplication(app.getId(), request, "admin");

        assertMoney("370000", replacement.getPrincipalAmount());
        assertEquals(0, ((BigDecimal) quote.get("principal")).compareTo(replacement.getPrincipalAmount()));
        assertEquals(original.getLoanId(), replacement.getRefinancedLoanId());
        assertEquals(futureStart, replacement.getStartDate());
        assertEquals(today, replacement.getDisbursementDate());
        assertEquals(firstEmi, replacement.getNextDueDate());
        assertEquals(12, replacement.getDurationMonths());
        var replacementSchedule = loans.getLoanSchedule(replacement.getId());
        assertMoney("3700", replacementSchedule.getFirst().getInterestAmount());
        assertMoney("0", replacementSchedule.getLast().getClosingPrincipal());
        assertEquals(0, replacement.getTotalInterest().compareTo(replacementSchedule.stream()
                .map(LoanRepaymentSchedule::getInterestAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));

        Loan closed = loans.getLoanById(original.getId());
        assertEquals(LoanStatus.CLOSED, closed.getStatus());
        assertEquals(today, closed.getSettlementDate());
        assertMoney("0", closed.getOutstandingPrincipal());
        assertMoney("10000", closed.getPrincipalPaid());
        assertMoney("10000", entries(TransactionType.EXTRA_LOAN_PAYMENT).getFirst().getAmount());
        assertEquals(replacement.getLoanId(), closed.getReplacementLoanId());
        assertEquals(RepaymentStatus.SETTLED, loans.getLoanSchedule(closed.getId()).getFirst().getStatus());
        assertEquals(0, beforeCash.subtract(new BigDecimal("100000")).compareTo(funds.getAvailableFund(group.getId())));
        assertMoney("270000", entries(TransactionType.ADJUSTMENT).getFirst().getPrincipalAmount());
        assertEquals(today, entries(TransactionType.ADJUSTMENT).getFirst().getDate());
        assertEquals(today, entries(TransactionType.LOAN_DISBURSEMENT).stream()
                .filter(entry -> replacement.getLoanId().equals(entry.getLoanId())).findFirst().orElseThrow().getDate());
        assertMoney("380000", entries(TransactionType.LOAN_DISBURSEMENT).stream()
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(0, replacement.getTotalOutstanding().compareTo(data.findGroupById(group.getId()).orElseThrow().getTotalLoanOutstanding()));
        assertThrows(InvalidFinancialOperationException.class,
                () -> applications.approveApplication(app.getId(), request, "admin"));
        assertEquals(1, entries(TransactionType.ADJUSTMENT).size());
        assertThrows(InvalidFinancialOperationException.class, () -> loans.recordRepayment(closed.getId(),
                new LoanRepaymentRequest(BigDecimal.ONE, today, "CASH", "OLD"), "admin"));
    }

    @Test
    void memberCapacityIsReadAgainAtApprovalAndCannotBypassGroupCapacity() {
        group.setMaxLoanAmount(new BigDecimal("350000"));
        group.setMaxLoanAmountPerMember(new BigDecimal("50000"));
        data.saveGroup(group);
        assertThrows(InvalidFinancialOperationException.class, () -> apply(member, 100_000, null));
        member.setLoanCapacity(new BigDecimal("300000"));
        data.saveMember(member);
        LoanApplication pending = apply(member, 100_000, null);
        assertMoney("300000", (BigDecimal) applications.getLoanEligibility(member.getMemberId()).get("maxAdditional"));

        member.setLoanCapacity(new BigDecimal("70000"));
        data.saveMember(member);
        assertMoney("70000", (BigDecimal) applications.getApprovalDetails(pending).get("maxAdditional"));
        assertThrows(InvalidFinancialOperationException.class, () -> applications.approveApplication(pending.getId(),
                terms(100_000, 12, today, today.plusMonths(1), "100"), "admin"));
        applications.approveApplication(pending.getId(), terms(70_000, 12, today, today.plusMonths(1), "100"), "admin");

        Member another = member("MEM-002", group);
        another.setLoanCapacity(new BigDecimal("400000"));
        data.saveMember(another);
        assertMoney("280000", (BigDecimal) applications.getLoanEligibility(another.getMemberId()).get("maxAdditional"));
        assertThrows(InvalidFinancialOperationException.class, () -> apply(another, 280_001, null));
    }

    @Test
    void exhaustedGroupCapExplainsBothNewLoanAndTopUpRejection() {
        LoanApplication app = apply(member, 270_000, null);
        Loan loan = applications.approveApplication(app.getId(),
                terms(270_000, 12, today, today.plusMonths(1), "100"), "admin");
        group.setMaxLoanAmount(BigDecimal.valueOf(200_000));
        group.setAllowMultipleLoans(true);
        group.setMaxActiveLoans(5);
        data.saveGroup(group);

        var eligibility = applications.getLoanEligibility(member.getMemberId());
        assertEquals(false, eligibility.get("eligible"));
        assertTrue(eligibility.get("reason").toString().contains("group's total lending cap"));
        var options = (List<?>) eligibility.get("topUpOptions");
        var topUp = (java.util.Map<?, ?>) options.getFirst();
        assertEquals(false, topUp.get("eligible"));
        assertTrue(topUp.get("reason").toString().contains("group's total lending cap"));
        var failure = assertThrows(InvalidFinancialOperationException.class, () -> apply(member, 100_000, loan.getId()));
        assertTrue(failure.getMessage().contains("group's total lending cap"));
    }

    @Test
    void availableCashPolicyAllowsTopUpAboveFixedGroupCapWithinMemberLimits() {
        group.setMaxLoanAmount(BigDecimal.valueOf(2_000_000));
        group.setAllowMultipleLoans(true);
        group.setMaxActiveLoans(5);
        data.saveGroup(group);
        Member other = member("OTHER-001", group);
        LoanApplication app = apply(member, 270_000, null);
        Loan loan = applications.approveApplication(app.getId(),
                terms(270_000, 12, today, today.plusMonths(1), "100"), "admin");
        LoanApplication otherApp = apply(other, 300_000, null);
        applications.approveApplication(otherApp.getId(),
                terms(300_000, 12, today, today.plusMonths(1), "100"), "admin");

        group.setMaxLoanAmount(BigDecimal.valueOf(500_000));
        group.setUseAvailableCashForGroupLoanLimit(true);
        data.saveGroup(group);
        var eligibility = applications.getLoanEligibility(member.getMemberId());
        assertEquals(true, eligibility.get("eligible"));
        assertMoney("230000", (BigDecimal) eligibility.get("maxAdditional"));
        assertThrows(InvalidFinancialOperationException.class, () -> apply(member, 230_001, loan.getId()));
        LoanApplication topUp = apply(member, 100_000, loan.getId());
        var topUpTerms = terms(100_000, 36, today.plusDays(5), today.plusDays(14), "100");
        topUpTerms.setExpectedOutstandingPrincipal(BigDecimal.valueOf(270_000));
        BigDecimal cashBefore = funds.getAvailableFund(group.getId());
        Loan replacement = applications.approveApplication(topUp.getId(), topUpTerms, "admin");
        assertMoney("370000", replacement.getPrincipalAmount());
        assertMoney("100000", cashBefore.subtract(funds.getAvailableFund(group.getId())));
    }

    @Test
    void availableCashPolicyStillRejectsRequestsAboveTheCashOnHand() {
        group.setUseAvailableCashForGroupLoanLimit(true);
        data.saveGroup(group);
        LoanApplication app = apply(member, 270_000, null);
        Loan loan = applications.approveApplication(app.getId(),
                terms(270_000, 12, today, today.plusMonths(1), "100"), "admin");
        BigDecimal expense = funds.getAvailableFund(group.getId()).subtract(BigDecimal.valueOf(50_000));
        data.saveTransaction(new Transaction("TXN-TEST-EXPENSE", group.getId(), null, null, null,
                TransactionType.EXPENSE, expense, BigDecimal.ZERO, BigDecimal.ZERO, today,
                "TEST-EXPENSE", "Group expense", "BANK_TRANSFER", "admin"));

        var eligibility = applications.getLoanEligibility(member.getMemberId());
        var topUp = (java.util.Map<?, ?>) ((List<?>) eligibility.get("topUpOptions")).getFirst();
        assertMoney("50000", (BigDecimal) topUp.get("maxAdditional"));
        assertThrows(com.bachatgat.exception.InsufficientGroupFundsException.class,
                () -> apply(member, 100_000, loan.getId()));
    }

    private Group group(String name) {
        Group group = new Group();
        group.setGroupName(name);
        return data.saveGroup(group);
    }

    private Member member(String id, Group group) {
        Member member = new Member();
        member.setMemberId(id);
        member.setFullName(id);
        member.setGroupId(group.getId());
        member.setTotalSavingsBalance(new BigDecimal("1000000"));
        return data.saveMember(member);
    }

    private LoanApplication apply(Member borrower, int amount, String topUpLoanId) {
        var request = new LoanApplicationRequest();
        request.setRequestedAmount(BigDecimal.valueOf(amount));
        request.setPreferredDurationMonths(12);
        request.setPurpose("Medical");
        request.setTopUpLoanId(topUpLoanId);
        return applications.submitApplication(borrower.getMemberId(), request);
    }

    private LoanApprovalRequest terms(int amount, int months, LocalDate start, LocalDate firstDue, String penalty) {
        var request = new LoanApprovalRequest();
        request.setApprovedAmount(BigDecimal.valueOf(amount));
        request.setDurationMonths(months);
        request.setInterestRate(BigDecimal.ONE);
        request.setIsMonthlyRate(true);
        request.setStartDate(start);
        request.setFirstEmiDueDate(firstDue);
        request.setEmiLatePenalty(new BigDecimal(penalty));
        return request;
    }

    private List<Transaction> entries(TransactionType type) {
        return data.getTransactionsByGroupId(group.getId()).stream().filter(entry -> entry.getType() == type).toList();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "Expected " + expected + ", got " + actual);
    }
}
