package com.bachatgat.service;

import com.bachatgat.dto.LoanApplicationRequest;
import com.bachatgat.dto.LoanApprovalRequest;
import com.bachatgat.dto.LoanExtraPaymentRequest;
import com.bachatgat.dto.LoanRepaymentRequest;
import com.bachatgat.dto.LoanPartialPaymentRequest;
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
class LoanApprovalAndRepaymentTest {

    @Autowired
    private LoanApplicationService loanApplicationService;

    @Autowired
    private LoanService loanService;

    @Autowired
    private FirestoreDataService dataService;

    @Test
    @DisplayName("Complete Loan Lifecycle: Application -> Admin Sanction -> Schedule -> Repayment -> Extra Prepayment")
    void testCompleteLoanLifecycle() {
        // 1. Savita Deshmukh submits loan application
        Member savita = dataService.findMemberByMemberId("SPBG-M003").orElseThrow();
        savita.setActiveLoanId(null);
        savita.setCurrentLoanOutstanding(BigDecimal.ZERO);
        dataService.saveMember(savita);

        LoanApplicationRequest appReq = new LoanApplicationRequest();
        appReq.setRequestedAmount(BigDecimal.valueOf(50000));
        appReq.setPurpose("Higher Education Course");
        appReq.setPreferredDurationMonths(10);
        appReq.setOptionalMessage("Fee installment payment deadline");

        LoanApplication app = loanApplicationService.submitApplication(savita.getMemberId(), appReq);
        assertNotNull(app.getId());
        assertEquals(LoanApplicationStatus.PENDING, app.getStatus());

        // 2. Admin approves with ₹40,000 sanctioned at 12% interest for 10 months
        LoanApprovalRequest approvalReq = new LoanApprovalRequest();
        approvalReq.setApprovedAmount(BigDecimal.valueOf(40000));
        approvalReq.setInterestRate(BigDecimal.valueOf(12.0));
        approvalReq.setDurationMonths(10);
        approvalReq.setInterestType("FLAT");
        approvalReq.setAdminNotes("Sanctioned ₹40,000 based on savings record");

        Loan loan = loanApplicationService.approveApplication(app.getId(), approvalReq, "admin");
        assertNotNull(loan);
        assertEquals(new BigDecimal("40000.00"), loan.getPrincipalAmount());
        assertEquals(new BigDecimal("4000.00"), loan.getTotalInterest());
        assertEquals(new BigDecimal("44000.00"), loan.getTotalPayable());
        assertEquals(new BigDecimal("4400.00"), loan.getMonthlyInstallment());
        assertEquals(LoanStatus.ACTIVE, loan.getStatus());

        // Check repayment schedule generated
        List<LoanRepaymentSchedule> schedules = loanService.getLoanSchedule(loan.getId());
        assertEquals(10, schedules.size());
        assertEquals(new BigDecimal("4400.00"), schedules.get(0).getTotalDue());

        // 3. Admin records regular installment payment: ₹4,400
        LoanRepaymentRequest repayReq = new LoanRepaymentRequest(
                BigDecimal.valueOf(4400), LocalDate.now(), "CASH", "REC-001");
        Loan afterRepay = loanService.recordRepayment(loan.getId(), repayReq, "admin");
        assertEquals(new BigDecimal("4000.00"), afterRepay.getPrincipalPaid());
        assertEquals(new BigDecimal("400.00"), afterRepay.getInterestPaid());
        assertEquals(new BigDecimal("4400.00"), afterRepay.getTotalPaid());
        assertEquals(new BigDecimal("39600.00"), afterRepay.getTotalOutstanding());

        // The repayment was recorded before the first due date in this test.
        // The schedule must still persist the actual principal component and
        // reduce closing principal; future-row pending visibility must not
        // prevent balance allocation.
        List<LoanRepaymentSchedule> afterRepaySchedule = loanService.getLoanSchedule(loan.getId());
        assertEquals(new BigDecimal("36000.00"), afterRepaySchedule.get(0).getClosingPrincipal());
        assertEquals(new BigDecimal("36000.00"), afterRepaySchedule.get(1).getOpeningPrincipal());

        // 4. Admin records extra principal prepayment of ₹10,000
        LoanExtraPaymentRequest extraReq = new LoanExtraPaymentRequest();
        extraReq.setAmount(BigDecimal.valueOf(10000));
        extraReq.setPaymentDate(LocalDate.now());
        extraReq.setPaymentMethod("UPI");
        extraReq.setReferenceNumber("UPI-EXTRA-999");

        Loan afterExtra = loanService.recordExtraPayment(loan.getId(), extraReq, "admin");
        assertEquals(new BigDecimal("14000.00"), afterExtra.getPrincipalPaid());
        assertEquals(new BigDecimal("29600.00"), afterExtra.getTotalOutstanding());
    }

    @Test
    @DisplayName("Partial interest-only payment leaves principal unchanged for the next month")
    void testInterestOnlyPartialPaymentDoesNotReducePrincipal() {
        Loan before = loanService.getLoanById("LN-2026-002");
        BigDecimal principalBefore = before.getOutstandingPrincipal();
        BigDecimal interestBefore = before.getOutstandingInterest();
        BigDecimal interestPaidBefore = before.getInterestPaid();
        BigDecimal principalPaidBefore = before.getPrincipalPaid();

        LoanPartialPaymentRequest request = new LoanPartialPaymentRequest();
        request.setAmount(new BigDecimal("500.00"));
        request.setLoanInterestAmount(new BigDecimal("500.00"));
        request.setPaymentDate(LocalDate.now());
        request.setPaymentMethod("CASH");
        request.setReferenceNumber("PARTIAL-INTEREST-" + System.nanoTime());

        Loan after = loanService.recordPartialPayment(before.getId(), request, "admin");

        assertEquals(0, principalBefore.compareTo(after.getOutstandingPrincipal()));
        assertEquals(0, interestBefore.subtract(new BigDecimal("500.00")).compareTo(after.getOutstandingInterest()));
        assertEquals(0, after.getPrincipalPaid().compareTo(principalPaidBefore));
        assertEquals(0, after.getInterestPaid().subtract(interestPaidBefore).compareTo(new BigDecimal("500.00")));
        assertTrue(dataService.getLoanSchedule(after.getId()).stream().anyMatch(item -> item.getStatus() == RepaymentStatus.PARTIAL));
    }
    @Test
    void reducingPrepaymentSurvivesScheduleReloadAndRejectsDuplicateReference() {
        LocalDate today = LocalDate.now();
        Loan loan = reducingLoan("PREPAY", today.minusDays(5));
        LoanExtraPaymentRequest request = new LoanExtraPaymentRequest();
        request.setAmount(new BigDecimal("10000.00"));
        request.setPaymentDate(today);
        request.setPaymentMethod("BANK_TRANSFER");
        request.setReferenceNumber("TEST-PREPAY");

        Loan saved = loanService.recordExtraPayment(loan.getId(), request, "admin");
        assertEquals(new BigDecimal("10000.00"), saved.getPrincipalPaid());
        assertEquals(new BigDecimal("50000.00"), saved.getOutstandingPrincipal());
        var freshSchedule = loanService.getLoanSchedule(loan.getId());
        assertEquals(new BigDecimal("50000.00"), freshSchedule.getFirst().getOpeningPrincipal());
        assertEquals(new BigDecimal("500.00"), freshSchedule.getFirst().getInterestAmount());
        assertEquals(new BigDecimal("50000.00"), loanService.getLoanById(loan.getId()).getOutstandingPrincipal());
        assertThrows(com.bachatgat.exception.InvalidFinancialOperationException.class,
                () -> loanService.recordExtraPayment(loan.getId(), request, "admin"));
        assertEquals(1, dataService.getTransactionsByMemberId(loan.getMemberId()).stream()
                .filter(entry -> entry.getType() == TransactionType.EXTRA_LOAN_PAYMENT).count());
    }

    @Test
    void principalOnlyCarriesInterestOnceAndProjectsNormalLaterPayments() {
        LocalDate today = LocalDate.now();
        Loan loan = reducingLoan("PRINCIPAL", today.minusDays(5));
        recordComponents(loan, today, "4731.00", "0.00");
        var rows = loanService.getLoanSchedule(loan.getId());
        assertEquals(new BigDecimal("600.00"), rows.getFirst().getPendingInterest());
        assertEquals(new BigDecimal("0.00"), rows.getFirst().getPendingPrincipal());
        assertEquals(new BigDecimal("55269.00"), rows.getFirst().getClosingPrincipal());
        assertEquals(new BigDecimal("552.69"), rows.get(1).getInterestAmount());
        assertEquals(new BigDecimal("5931.00"), rows.get(1).getTotalDue());
        assertEquals(new BigDecimal("0.00"), rows.get(1).getOutstandingAmount());
        assertEquals(new BigDecimal("0.00"), rows.get(2).getPreviousPendingInterest());
        assertEquals(new BigDecimal("50490.69"), rows.get(2).getOpeningPrincipal());
    }

    @Test
    void catchUpCollectsMissedPrincipalAndCurrentInstallmentWithoutDoubleCharging() {
        LocalDate today = LocalDate.now();
        Loan loan = reducingLoan("CATCHUP", today.minusMonths(2));
        recordComponents(loan, loan.getDisbursementDate().plusMonths(1), "0.00", "600.00");
        var arrears = loanService.getLoanSchedule(loan.getId());
        assertEquals(new BigDecimal("60000.00"), arrears.getFirst().getClosingPrincipal());
        assertEquals(new BigDecimal("4731.00"), arrears.get(1).getPreviousPendingPrincipal());
        assertEquals(new BigDecimal("600.00"), arrears.get(1).getInterestAmount());
        recordComponents(loan, today, "9509.31", "600.00");
        var caughtUp = loanService.getLoanSchedule(loan.getId());
        assertEquals(new BigDecimal("60000.00"), caughtUp.getFirst().getClosingPrincipal());
        assertEquals(new BigDecimal("50490.69"), caughtUp.get(1).getClosingPrincipal());
        assertEquals(new BigDecimal("504.91"), caughtUp.get(2).getInterestAmount());
        assertEquals(new BigDecimal("0.00"), caughtUp.get(2).getPreviousPendingPrincipal());
    }

    @Test
    void stalePaymentCannotOverwriteAnAlreadyCommittedPrepayment() {
        LocalDate today = LocalDate.now();
        Loan loan = reducingLoan("STALE", today.minusDays(5));
        var stale = dataService.beginLoanPayment(loan);
        var staleSchedule = dataService.getLoanScheduleForPayment(loan.getId());
        LoanExtraPaymentRequest request = new LoanExtraPaymentRequest();
        request.setAmount(new BigDecimal("10000.00"));
        request.setPaymentDate(today);
        request.setReferenceNumber("TEST-STALE-COMMITTED");
        loanService.recordExtraPayment(loan.getId(), request, "admin");

        Transaction staleReceipt = new Transaction("TEST-STALE-RECEIPT", loan.getGroupId(), loan.getMemberId(),
                loan.getMemberName(), loan.getLoanId(), TransactionType.EXTRA_LOAN_PAYMENT, request.getAmount(),
                request.getAmount(), BigDecimal.ZERO, today, "TEST-STALE-REJECTED", "Test", "CASH", "admin");
        assertThrows(IllegalStateException.class, () -> dataService.saveLoanPayment(stale, staleSchedule, staleReceipt));
        assertTrue(dataService.findTransactionByReference("TEST-STALE-REJECTED").isEmpty());
        assertEquals(new BigDecimal("50000.00"), loanService.getLoanSchedule(loan.getId()).getFirst().getOpeningPrincipal());
    }

    private Loan reducingLoan(String suffix, LocalDate disbursed) {
        Loan loan = new Loan();
        loan.setLoanId("TEST-" + suffix);
        loan.setGroupId("TEST-PAYMENT-GROUP");
        loan.setMemberId("TEST-" + suffix);
        loan.setMemberName("Payment test");
        loan.setPrincipalAmount(new BigDecimal("60000.00"));
        loan.setDisbursementDate(disbursed);
        return dataService.saveLoan(loan);
    }

    private void recordComponents(Loan loan, LocalDate date, String principal, String interest) {
        LoanPartialPaymentRequest request = new LoanPartialPaymentRequest();
        request.setLoanPrincipalAmount(new BigDecimal(principal));
        request.setLoanInterestAmount(new BigDecimal(interest));
        request.setAmount(request.getLoanPrincipalAmount().add(request.getLoanInterestAmount()));
        request.setPaymentDate(date);
        request.setPaymentMethod("CASH");
        request.setReferenceNumber(loan.getLoanId() + "-" + date);
        loanService.recordPartialPayment(loan.getId(), request, "admin");
    }
}
