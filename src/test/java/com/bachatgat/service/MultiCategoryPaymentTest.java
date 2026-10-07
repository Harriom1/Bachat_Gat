package com.bachatgat.service;

import com.bachatgat.dto.CollectionPaymentRequest;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MultiCategoryPaymentTest {

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private FirestoreDataService dataService;

    @Autowired
    private LoanService loanService;

    @BeforeEach
    void setUp() {
        dataService.seedInitialDemoData();
    }

    @Test
    @DisplayName("Option 1 + Option 2 + Option 3 allocation matches total amount and updates balances correctly")
    void testThreeCategoryPaymentSuccess() {
        String groupId = "bg-001";
        String memberId = "MPBG-M001";
        int month = 7;
        int year = 2026;

        Member memberBefore = dataService.getMemberById(memberId);
        assertNotNull(memberBefore, "Member MPBG-M001 must exist in seed data");
        BigDecimal initialSavings = memberBefore.getTotalSavings();
        BigDecimal expectedShare = memberBefore.getMonthlyShareAmount() != null ? memberBefore.getMonthlyShareAmount() : new BigDecimal("5000.00");
        BigDecimal loanPrin = new BigDecimal("4000.00");
        BigDecimal loanInt = new BigDecimal("100.00");
        BigDecimal totalExpected = expectedShare.add(loanPrin).add(loanInt);

        CollectionPaymentRequest req = new CollectionPaymentRequest();
        req.setMemberId(memberId);
        req.setMonth(month);
        req.setYear(year);
        req.setAmount(totalExpected);
        req.setShareAmount(expectedShare); // Option 1
        req.setLoanPrincipalAmount(loanPrin); // Option 2 (Principal)
        req.setLoanInterestAmount(loanInt); // Option 2 (Interest)
        req.setOtherAmount(BigDecimal.ZERO); // Option 3
        req.setPaymentMethod("UPI");
        req.setReferenceNumber("UTR-TEST-9100");
        req.setIdempotencyKey("IDEMP-TEST-SUCCESS-" + System.currentTimeMillis());
        req.setPaymentStatus("SUCCESS");
        req.setPaymentDate(LocalDate.of(year, month, 5)); // Paid on time before the 10th

        CollectionRecord record = collectionService.recordPayment(groupId, req, "admin");
        assertNotNull(record);
        assertEquals(CollectionStatus.PAID, record.getStatus());
        assertEquals(expectedShare, record.getPaidAmount());

        // Verify member savings increased by share component + distributed interest dividend (₹100 / 5 members = ₹20.00)
        Member memberAfter = dataService.getMemberById(memberId);
        BigDecimal distributedInterestShare = loanInt.divide(new BigDecimal("5"), 2, java.math.RoundingMode.HALF_UP);
        assertEquals(0, initialSavings.add(expectedShare).add(distributedInterestShare).compareTo(memberAfter.getTotalSavings()));

        // Verify separate transactions exist in ledger
        List<Transaction> transactions = dataService.getTransactionsByGroupId(groupId);
        boolean shareTxFound = transactions.stream()
                .anyMatch(t -> t.getType() == TransactionType.SHARE_CONTRIBUTION &&
                        t.getAmount().compareTo(expectedShare) == 0);
        boolean loanTxFound = transactions.stream()
                .anyMatch(t -> (t.getType() == TransactionType.LOAN_REPAYMENT || t.getType() == TransactionType.LOAN_PRINCIPAL_PAYMENT) &&
                        t.getAmount().compareTo(new BigDecimal("4100.00")) == 0);

        assertTrue(shareTxFound, "Share contribution ledger entry must be created");
        assertTrue(loanTxFound, "Loan repayment ledger entry must be created for 4100.00");
    }

    @Test
    @DisplayName("Validation fails when total allocated does not equal payment amount received")
    void testAllocationMismatchFails() {
        String groupId = "bg-001";
        String memberId = "MPBG-M002";

        CollectionPaymentRequest req = new CollectionPaymentRequest();
        req.setMemberId(memberId);
        req.setMonth(8);
        req.setYear(2026);
        req.setAmount(new BigDecimal("9100.00"));
        req.setShareAmount(new BigDecimal("5000.00"));
        req.setLoanPrincipalAmount(new BigDecimal("4000.00")); // Sum = 9000, but amount = 9100!
        req.setLoanInterestAmount(BigDecimal.ZERO);
        req.setOtherAmount(BigDecimal.ZERO);
        req.setPaymentMethod("CASH");
        req.setIdempotencyKey("IDEMP-TEST-MISMATCH-" + System.currentTimeMillis());
        req.setPaymentStatus("SUCCESS");

        com.bachatgat.exception.InvalidFinancialOperationException ex = assertThrows(
                com.bachatgat.exception.InvalidFinancialOperationException.class, () ->
                collectionService.recordPayment(groupId, req, "admin"));
        assertTrue(ex.getMessage().contains("Total allocated amount"), "Must reject allocation sum mismatch");
    }

    @Test
    @DisplayName("Duplicate idempotency key is rejected and prevents double-crediting")
    void testDuplicateIdempotencyRejected() {
        String groupId = "bg-001";
        String memberId = "MPBG-M003";
        String idempotencyKey = "IDEMP-DUP-" + System.currentTimeMillis();

        CollectionPaymentRequest req1 = new CollectionPaymentRequest();
        req1.setMemberId(memberId);
        req1.setMonth(9);
        req1.setYear(2026);
        req1.setAmount(new BigDecimal("5000.00"));
        req1.setShareAmount(new BigDecimal("5000.00"));
        req1.setIdempotencyKey(idempotencyKey);
        req1.setPaymentMethod("UPI");
        req1.setPaymentStatus("SUCCESS");

        collectionService.recordPayment(groupId, req1, "admin");

        // Attempting identical duplicate payment
        CollectionPaymentRequest req2 = new CollectionPaymentRequest();
        req2.setMemberId(memberId);
        req2.setMonth(9);
        req2.setYear(2026);
        req2.setAmount(new BigDecimal("5000.00"));
        req2.setShareAmount(new BigDecimal("5000.00"));
        req2.setIdempotencyKey(idempotencyKey);
        req2.setPaymentMethod("UPI");
        req2.setPaymentStatus("SUCCESS");

        com.bachatgat.exception.DuplicateRecordException ex = assertThrows(
                com.bachatgat.exception.DuplicateRecordException.class, () ->
                collectionService.recordPayment(groupId, req2, "admin"));
        assertTrue(ex.getMessage().contains("already processed"), "Duplicate idempotency key must be rejected");
    }

    @Test
    @DisplayName("FAILED payment status creates no financial balances or successful ledger entries")
    void testFailedPaymentDoesNotUpdateBalances() {
        String groupId = "bg-001";
        String memberId = "MPBG-M004";
        Member before = dataService.getMemberById(memberId);
        assertNotNull(before, "Member MPBG-M004 must exist in seed data");
        BigDecimal beforeSavings = before.getTotalSavings();

        CollectionPaymentRequest req = new CollectionPaymentRequest();
        req.setMemberId(memberId);
        req.setMonth(10);
        req.setYear(2026);
        req.setAmount(new BigDecimal("5000.00"));
        req.setPaymentStatus("FAILED");
        req.setPaymentMethod("UPI");
        req.setIdempotencyKey("IDEMP-FAIL-" + System.currentTimeMillis());

        CollectionRecord res = collectionService.recordPayment(groupId, req, "admin");
        assertEquals(CollectionStatus.FAILED, res.getStatus());

        Member after = dataService.getMemberById(memberId);
        assertEquals(beforeSavings, after.getTotalSavings(), "Savings balance must NOT change on failed payment");
    }
}
