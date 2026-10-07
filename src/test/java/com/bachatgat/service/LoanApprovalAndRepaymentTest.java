package com.bachatgat.service;

import com.bachatgat.dto.LoanApplicationRequest;
import com.bachatgat.dto.LoanApprovalRequest;
import com.bachatgat.dto.LoanExtraPaymentRequest;
import com.bachatgat.dto.LoanRepaymentRequest;
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
}
