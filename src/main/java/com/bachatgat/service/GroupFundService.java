package com.bachatgat.service;

import com.bachatgat.model.Group;
import com.bachatgat.model.GroupExpense;
import com.bachatgat.model.GroupIncome;
import com.bachatgat.model.Loan;
import com.bachatgat.model.LoanStatus;
import com.bachatgat.model.Transaction;
import com.bachatgat.model.TransactionType;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Single backend calculation for the group's cash available for new lending.
 * The ledger is preferred; the entity aggregates are used only for legacy data
 * that predates ledger entries.
 */
@Service
public class GroupFundService {

    private static final Set<TransactionType> CREDIT_TYPES = EnumSet.of(
            TransactionType.SHARE_CONTRIBUTION,
            TransactionType.SHARE_COLLECTION,
            TransactionType.MEMBER_SAVING,
            TransactionType.LOAN_PRINCIPAL_PAYMENT,
            TransactionType.LOAN_PRINCIPAL_REPAYMENT,
            TransactionType.LOAN_REPAYMENT,
            TransactionType.LOAN_EXTRA_PAYMENT,
            TransactionType.EXTRA_LOAN_PAYMENT,
            TransactionType.LOAN_INTEREST_PAYMENT,
            TransactionType.LOAN_INTEREST_INCOME,
            TransactionType.LOAN_INTEREST,
            TransactionType.LOAN_PENALTY_INCOME,
            TransactionType.SHARE_LATE_FEE,
            TransactionType.OTHER_INCOME
    );

    private static final Set<TransactionType> DEBIT_TYPES = EnumSet.of(
            TransactionType.LOAN_DISBURSEMENT,
            TransactionType.EXPENSE,
            TransactionType.OTHER_EXPENSE,
            TransactionType.BANK_WITHDRAWAL
    );

    private final FirestoreDataService dataService;

    public GroupFundService(FirestoreDataService dataService) {
        this.dataService = dataService;
    }

    public BigDecimal getAvailableFund(String groupId) {
        return getAvailableFund(groupId, null);
    }

    public BigDecimal getAvailableFundBefore(String groupId, LocalDate date) {
        return getAvailableFund(groupId, date);
    }

    private BigDecimal getAvailableFund(String groupId, LocalDate before) {
        List<Transaction> transactions = dataService.getTransactionsByGroupId(groupId).stream()
                .filter(t -> isSuccessful(t) && (before == null || (t.getDate() != null && t.getDate().isBefore(before))))
                .toList();

        if (!transactions.isEmpty()) {
            return transactions.stream().map(this::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add).max(BigDecimal.ZERO);
        }

        // Safe compatibility path for records created before the central ledger.
        Group group = dataService.findGroupById(groupId).orElse(null);
        if (group == null) return BigDecimal.ZERO;
        if (before != null) return BigDecimal.ZERO;

        BigDecimal savings = dataService.getMembersByGroupId(groupId).stream()
                .map(m -> nz(m.getTotalSavingsBalance())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal principalRecovered = dataService.getLoansByGroupId(groupId).stream()
                .map(l -> nz(l.getPrincipalPaid())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interest = dataService.getLoansByGroupId(groupId).stream()
                .map(l -> nz(l.getInterestPaid())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal income = dataService.getIncomesByGroupId(groupId).stream()
                .map(i -> nz(i.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenses = dataService.getExpensesByGroupId(groupId).stream()
                .map(e -> nz(e.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal disbursed = dataService.getLoansByGroupId(groupId).stream()
                .filter(l -> l.getStatus() != LoanStatus.PENDING && l.getStatus() != LoanStatus.APPROVED && l.getStatus() != LoanStatus.CANCELLED)
                .map(l -> nz(l.getPrincipalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        return savings.add(principalRecovered).add(interest).add(income).subtract(expenses).subtract(disbursed).max(BigDecimal.ZERO);
    }

    public BigDecimal getBalanceByPaymentMethod(String groupId, boolean bank) {
        return dataService.getTransactionsByGroupId(groupId).stream()
                .filter(this::isSuccessful)
                .filter(t -> isBankPayment(t) == bank)
                .map(this::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .max(BigDecimal.ZERO);
    }

    private BigDecimal signedAmount(Transaction transaction) {
        BigDecimal amount = nz(transaction.getAmount());
        if (CREDIT_TYPES.contains(transaction.getType())) return amount;
        if (DEBIT_TYPES.contains(transaction.getType())) return amount.negate();
        return BigDecimal.ZERO;
    }

    private boolean isSuccessful(Transaction transaction) {
        return transaction.getPaymentStatus() == null || "SUCCESS".equalsIgnoreCase(transaction.getPaymentStatus());
    }

    private boolean isBankPayment(Transaction transaction) {
        String method = transaction.getPaymentMethod();
        if (method == null) return false;
        String normalized = method.toUpperCase();
        return normalized.contains("BANK") || normalized.contains("UPI") || normalized.contains("ONLINE") || normalized.contains("CARD") || normalized.contains("WALLET");
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
