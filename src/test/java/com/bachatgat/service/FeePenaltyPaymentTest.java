package com.bachatgat.service;

import com.bachatgat.dto.CollectionPaymentRequest;
import com.bachatgat.exception.DuplicateRecordException;
import com.bachatgat.exception.InvalidFinancialOperationException;
import com.bachatgat.model.CollectionRecord;
import com.bachatgat.model.GroupIncome;
import com.bachatgat.model.Transaction;
import com.bachatgat.model.TransactionType;
import com.bachatgat.repository.FirestoreDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class FeePenaltyPaymentTest {

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private FirestoreDataService dataService;

    @BeforeEach
    void setUp() {
        dataService.seedInitialDemoData();
    }

    @Test
    @DisplayName("Paid late fee is saved to the collection, penalty transaction, and group income ledgers")
    void paidLateFeeIsPersistedEverywhere() {
        String reference = unique("FEE");
        String idempotencyKey = unique("FEE-IDEMP");

        CollectionPaymentRequest request = baseRequest("MPBG-M001", 12, 2099, reference, idempotencyKey);
        request.setAmount(new BigDecimal("125.00"));
        request.setShareAmount(BigDecimal.ZERO);
        request.setLateFeeAmount(new BigDecimal("125.00"));
        request.setWaivedLateFee(BigDecimal.ZERO);

        CollectionRecord result = collectionService.recordPayment("bg-001", request, "admin");

        CollectionRecord saved = dataService.findCollectionByBusinessKey("bg-001_MPBG-M001_12_2099").orElseThrow();
        assertEquals(new BigDecimal("125.00"), saved.getLateFeeAmount());
        assertEquals(new BigDecimal("125.00"), saved.getPaidLateFee());
        assertEquals(result.getId(), saved.getId());

        Transaction transaction = dataService.findTransactionByReference(reference).orElseThrow();
        assertEquals(TransactionType.PENALTY_INCOME, transaction.getType());
        assertEquals(new BigDecimal("125.00"), transaction.getAmount());
        assertEquals(idempotencyKey, transaction.getIdempotencyKey());

        GroupIncome income = dataService.getIncomesByGroupId("bg-001").stream()
                .filter(item -> reference.equals(item.getReference()))
                .findFirst()
                .orElseThrow();
        assertEquals("PENALTY", income.getCategory());
        assertEquals(new BigDecimal("125.00"), income.getAmount());
    }

    @Test
    @DisplayName("A payment lower than the penalty is rejected")
    void paymentCannotBeLowerThanPenalty() {
        CollectionPaymentRequest request = baseRequest("MPBG-M005", 12, 2099,
                unique("UNDER-PENALTY"), unique("UNDER-PENALTY-IDEMP"));
        request.setAmount(new BigDecimal("20.00"));
        request.setShareAmount(BigDecimal.ZERO);
        request.setLateFeeAmount(new BigDecimal("125.00"));
        request.setWaivedLateFee(BigDecimal.ZERO);

        InvalidFinancialOperationException error = assertThrows(InvalidFinancialOperationException.class,
                () -> collectionService.recordPayment("bg-001", request, "admin"));

        assertTrue(error.getMessage().contains("cannot be less than the penalty of ₹125.00"));
    }

    @Test
    @DisplayName("A waived late fee is saved as waived and does not create penalty income")
    void waivedLateFeeIsPersistedWithoutChargingIt() {
        String reference = unique("WAIVED");
        CollectionPaymentRequest request = baseRequest("MPBG-M002", 11, 2099, reference, unique("WAIVED-IDEMP"));
        request.setAmount(new BigDecimal("1000.00"));
        request.setShareAmount(new BigDecimal("1000.00"));
        request.setLateFeeAmount(new BigDecimal("100.00"));
        request.setWaivedLateFee(new BigDecimal("100.00"));

        CollectionRecord saved = collectionService.recordPayment("bg-001", request, "admin");

        assertEquals(new BigDecimal("100.00"), saved.getLateFeeAmount());
        assertEquals(new BigDecimal("100.00"), saved.getWaivedLateFee());
        assertEquals(0, saved.getPaidLateFee().compareTo(BigDecimal.ZERO));
        assertTrue(dataService.getTransactionsByGroupId("bg-001").stream()
                .noneMatch(item -> reference.equals(item.getReference())
                        && item.getType() == TransactionType.PENALTY_INCOME));
        assertTrue(dataService.getIncomesByGroupId("bg-001").stream()
                .noneMatch(item -> reference.equals(item.getReference())));
    }

    @Test
    @DisplayName("Other-income allocation is saved to both the income and transaction ledgers")
    void otherIncomeIsPersisted() {
        String reference = unique("OTHER");
        String idempotencyKey = unique("OTHER-IDEMP");
        CollectionPaymentRequest request = baseRequest("MPBG-M003", 10, 2099, reference, idempotencyKey);
        request.setAmount(new BigDecimal("275.00"));
        request.setShareAmount(BigDecimal.ZERO);
        request.setOtherAmount(new BigDecimal("275.00"));
        request.setOtherCategory("MISC");
        request.setNotes("Test other income");

        collectionService.recordPayment("bg-001", request, "admin");

        Transaction transaction = dataService.findTransactionByReference(reference).orElseThrow();
        assertEquals(TransactionType.OTHER_INCOME, transaction.getType());
        assertEquals(new BigDecimal("275.00"), transaction.getAmount());
        assertEquals(idempotencyKey, transaction.getIdempotencyKey());

        GroupIncome income = dataService.getIncomesByGroupId("bg-001").stream()
                .filter(item -> reference.equals(item.getReference()))
                .findFirst()
                .orElseThrow();
        assertEquals("MISC", income.getSource());
        assertEquals("MISC", income.getCategory());
        assertEquals(new BigDecimal("275.00"), income.getAmount());
        assertNotNull(income.getIncomeDate());
    }

    @Test
    @DisplayName("Other-income idempotency key prevents a second save")
    void otherIncomeDuplicateIsRejected() {
        String reference = unique("OTHER-DUP");
        String idempotencyKey = unique("OTHER-DUP-IDEMP");
        CollectionPaymentRequest request = baseRequest("MPBG-M004", 9, 2099, reference, idempotencyKey);
        request.setAmount(new BigDecimal("50.00"));
        request.setShareAmount(BigDecimal.ZERO);
        request.setOtherAmount(new BigDecimal("50.00"));

        collectionService.recordPayment("bg-001", request, "admin");

        assertThrows(DuplicateRecordException.class,
                () -> collectionService.recordPayment("bg-001", request, "admin"));
    }

    private CollectionPaymentRequest baseRequest(String memberId, int month, int year,
                                                   String reference, String idempotencyKey) {
        CollectionPaymentRequest request = new CollectionPaymentRequest();
        request.setMemberId(memberId);
        request.setMonth(month);
        request.setYear(year);
        request.setPaymentMethod("CASH");
        request.setPaymentDate(LocalDate.of(year, month, 20));
        request.setReferenceNumber(reference);
        request.setIdempotencyKey(idempotencyKey);
        request.setPaymentStatus("SUCCESS");
        return request;
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
