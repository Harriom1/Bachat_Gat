package com.bachatgat.service;

import com.bachatgat.model.Transaction;
import com.bachatgat.model.TransactionType;
import com.bachatgat.model.CollectionRecord;
import com.bachatgat.model.GroupExpense;
import com.bachatgat.model.GroupIncome;
import com.bachatgat.model.Loan;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final FirestoreDataService dataService;

    public TransactionService(FirestoreDataService dataService) {
        this.dataService = dataService;
    }

    public Transaction recordTransaction(String groupId, String memberId, String memberName, String loanId,
                                         TransactionType type, BigDecimal amount, BigDecimal principalAmount,
                                         BigDecimal interestAmount, LocalDate date, String reference,
                                         String description, String paymentMethod, String createdBy) {
        String txnId = "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        Transaction transaction = new Transaction(txnId, groupId, memberId, memberName, loanId, type,
                amount, principalAmount, interestAmount, date, reference, description, paymentMethod, createdBy);
        return dataService.saveTransaction(transaction);
    }

    public List<Transaction> getGroupTransactions(String groupId) {
        return dataService.getTransactionsByGroupId(groupId);
    }

    public List<Transaction> getMemberTransactions(String memberId) {
        return dataService.getTransactionsByMemberId(memberId);
    }

    public List<Transaction> getFilteredTransactions(String groupId, String memberId, Integer month, Integer year, String type) {
        List<Transaction> list;
        if (memberId != null && !memberId.isBlank()) {
            list = dataService.getTransactionsByMemberId(memberId);
        } else {
            list = dataService.getTransactionsByGroupId(groupId);
        }

        return list.stream()
                .filter(t -> {
                    if (groupId != null && !groupId.isBlank() && !groupId.equalsIgnoreCase(t.getGroupId())) {
                        return false;
                    }
                    if (memberId != null && !memberId.isBlank() && !memberId.equalsIgnoreCase(t.getMemberId())) {
                        return false;
                    }
                    if (month != null && month > 0 && t.getDate() != null && t.getDate().getMonthValue() != month) {
                        return false;
                    }
                    if (year != null && year > 0 && t.getDate() != null && t.getDate().getYear() != year) {
                        return false;
                    }
                    if (type != null && !type.isBlank() && !type.equalsIgnoreCase("ALL")
                            && !matchesCategory(t.getType(), type)) return false;
                    return true;
                })
                .toList();
    }

    /**
     * Returns the read-only ledger view used by the admin transaction screen.
     * Older/demo data may have been saved in the collections, loans, expenses,
     * or income stores before a central Transaction was created. Those records
     * are projected here for display only; the source records are never copied
     * back into the transaction store.
     */
    public List<Transaction> getDisplayTransactions(String groupId) {
        List<Transaction> result = new ArrayList<>(dataService.getTransactionsByGroupId(groupId));
        Set<String> keys = result.stream().map(this::displayKey).collect(Collectors.toCollection(HashSet::new));

        for (CollectionRecord record : dataService.getCollectionsByGroupId(groupId)) {
            BigDecimal paid = safe(record.getPaidAmount());
            if (paid.signum() <= 0) continue;
            LocalDate date = record.getPaymentDate() != null
                    ? record.getPaymentDate()
                    : LocalDate.of(record.getYear(), record.getMonth(), 1);
            Transaction projected = new Transaction(
                    "COL-" + safeId(record.getId()), record.getGroupId(), record.getMemberId(),
                    record.getMemberName(), null, TransactionType.SHARE_CONTRIBUTION, paid, paid,
                    BigDecimal.ZERO, date, record.getReferenceNumber(),
                    "Monthly Bachat Contribution for " + record.getMonth() + "/" + record.getYear(),
                    record.getPaymentMethod(), record.getRecordedBy());
            projected.setCategory("SHARE");
            addIfMissing(result, keys, projected);
        }

        Set<String> loanIds = new HashSet<>();
        for (Loan loan : dataService.getLoansByGroupId(groupId)) {
            if (loan == null || !loanIds.add(safeId(loan.getId()))) continue;
            BigDecimal principal = safe(loan.getPrincipalAmount());
            if (principal.signum() <= 0) continue;
            Transaction projected = new Transaction(
                    "LOAN-" + safeId(loan.getLoanId() != null ? loan.getLoanId() : loan.getId()),
                    loan.getGroupId(), loan.getMemberId(), loan.getMemberName(), loan.getLoanId(),
                    TransactionType.LOAN_DISBURSEMENT, principal, principal, BigDecimal.ZERO,
                    loan.getDisbursementDate(), null, "Loan disbursement for " + loan.getPurpose(),
                    "BANK_TRANSFER", loan.getCreatedBy());
            projected.setCategory("LOAN");
            addIfMissing(result, keys, projected);
        }

        for (GroupExpense expense : dataService.getExpensesByGroupId(groupId)) {
            BigDecimal amount = safe(expense.getAmount());
            if (amount.signum() <= 0) continue;
            Transaction projected = new Transaction(
                    "EXP-" + safeId(expense.getId()), expense.getGroupId(), null, null, null,
                    TransactionType.EXPENSE, amount, BigDecimal.ZERO, BigDecimal.ZERO,
                    expense.getExpenseDate(), expense.getReference(), expense.getDescription(),
                    expense.getPaymentMode(), expense.getRecordedBy());
            projected.setCategory("EXPENSE");
            addIfMissing(result, keys, projected);
        }

        for (GroupIncome income : dataService.getIncomesByGroupId(groupId)) {
            BigDecimal amount = safe(income.getAmount());
            if (amount.signum() <= 0) continue;
            Transaction projected = new Transaction(
                    "INC-" + safeId(income.getId()), income.getGroupId(), null, null, null,
                    TransactionType.OTHER_INCOME, amount, BigDecimal.ZERO, BigDecimal.ZERO,
                    income.getIncomeDate(), income.getReference(), income.getDescription(),
                    income.getPaymentMethod(), income.getRecordedBy());
            projected.setCategory("OTHER");
            addIfMissing(result, keys, projected);
        }

        return result.stream()
                .sorted((a, b) -> safeDate(b).compareTo(safeDate(a)))
                .toList();
    }

    private void addIfMissing(List<Transaction> result, Set<String> keys, Transaction candidate) {
        if (keys.add(displayKey(candidate))) result.add(candidate);
    }

    private String displayKey(Transaction transaction) {
        return String.join("|", String.valueOf(transaction.getType()),
                String.valueOf(transaction.getMemberId()), String.valueOf(transaction.getLoanId()),
                String.valueOf(transaction.getDate()), safe(transaction.getAmount()).toPlainString());
    }

    private boolean matchesCategory(TransactionType actual, String requested) {
        if (actual == null) return false;
        String value = requested.toUpperCase();
        return switch (value) {
            case "MEMBER_SAVING" -> actual == TransactionType.MEMBER_SAVING
                    || actual == TransactionType.SHARE_CONTRIBUTION
                    || actual == TransactionType.SHARE_COLLECTION;
            case "LOAN_REPAYMENT" -> actual == TransactionType.LOAN_REPAYMENT
                    || actual == TransactionType.LOAN_PRINCIPAL_PAYMENT
                    || actual == TransactionType.LOAN_PRINCIPAL_REPAYMENT
                    || actual == TransactionType.LOAN_INTEREST_PAYMENT;
            case "EXTRA_LOAN_PAYMENT" -> actual == TransactionType.EXTRA_LOAN_PAYMENT
                    || actual == TransactionType.LOAN_EXTRA_PAYMENT;
            case "OTHER_INCOME" -> actual == TransactionType.OTHER_INCOME
                    || actual == TransactionType.LOAN_INTEREST_INCOME
                    || actual == TransactionType.LOAN_PENALTY_INCOME
                    || actual == TransactionType.SHARE_LATE_FEE;
            default -> actual.name().equalsIgnoreCase(requested);
        };
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String safeId(String value) {
        return value != null && !value.isBlank() ? value : "unknown";
    }

    private LocalDate safeDate(Transaction transaction) {
        return transaction.getDate() != null ? transaction.getDate() : LocalDate.MIN;
    }
}
