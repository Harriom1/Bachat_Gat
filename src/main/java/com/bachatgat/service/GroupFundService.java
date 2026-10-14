package com.bachatgat.service;

import com.bachatgat.model.Group;
import com.bachatgat.model.GroupExpense;
import com.bachatgat.model.GroupIncome;
import com.bachatgat.model.Loan;
import com.bachatgat.model.LoanStatus;
import com.bachatgat.model.MemberStatus;
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
            TransactionType.BANK_WITHDRAWAL,
            // Exit refunds reserve group cash immediately. Bank processing later
            // updates only the refund status and must not debit cash twice.
            TransactionType.REFUND
    );

    private final FirestoreDataService dataService;

    public GroupFundService(FirestoreDataService dataService) {
        this.dataService = dataService;
    }

    public BigDecimal getAvailableFund(String groupId) {
        return getFundBreakdown(groupId).availableFund();
    }

    /**
     * Amount that can safely be lent. Reporting keeps a signed balance so a
     * shortfall is visible, while lending and refund checks cannot use a
     * negative balance as if it were available capacity.
     */
    public BigDecimal getLendingCapacity(String groupId) {
        return getAvailableFund(groupId).max(BigDecimal.ZERO);
    }

    public BigDecimal getAvailableFundBefore(String groupId, LocalDate date) {
        return getAvailableFund(groupId, date);
    }

    private BigDecimal getAvailableFund(String groupId, LocalDate before) {
        List<Transaction> transactions = dataService.getTransactionsByGroupId(groupId).stream()
                .filter(t -> isSuccessful(t) && (before == null || (t.getDate() != null && t.getDate().isBefore(before))))
                .toList();

        if (!transactions.isEmpty()) {
            // Keep the signed balance. Clamping a shortfall to zero hides real
            // changes (for example, a new savings payment cannot be seen while
            // the group is still below its disbursed-loan position).
            return transactions.stream().map(this::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
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

        return savings.add(principalRecovered).add(interest).add(income).subtract(expenses).subtract(disbursed);
    }

    /**
     * Single source of truth for the dashboard accounting breakdown. Every
     * displayed component and the final available fund are derived from the
     * same successful ledger entries, so the numbers cannot drift apart.
     */
    public FundBreakdown getFundBreakdown(String groupId) {
        List<Transaction> transactions = dataService.getTransactionsByGroupId(groupId).stream()
                .filter(this::isSuccessful).toList();
        if (!transactions.isEmpty()) {
            BigDecimal savings = BigDecimal.ZERO;
            BigDecimal interest = BigDecimal.ZERO;
            BigDecimal otherIncome = BigDecimal.ZERO;
            BigDecimal expenses = BigDecimal.ZERO;
            BigDecimal disbursed = BigDecimal.ZERO;
            BigDecimal principalRepaid = BigDecimal.ZERO;
            BigDecimal refunds = BigDecimal.ZERO;

            for (Transaction transaction : transactions) {
                TransactionType type = transaction.getType();
                BigDecimal amount = nz(transaction.getAmount());
                BigDecimal principal = nz(transaction.getPrincipalAmount());
                BigDecimal interestComponent = nz(transaction.getInterestAmount());
                if (type == null) continue;
                if (type == TransactionType.SHARE_CONTRIBUTION || type == TransactionType.SHARE_COLLECTION || type == TransactionType.MEMBER_SAVING) {
                    savings = savings.add(amount);
                } else if (type == TransactionType.LOAN_DISBURSEMENT) {
                    disbursed = disbursed.add(amount);
                } else if (type == TransactionType.EXPENSE || type == TransactionType.OTHER_EXPENSE || type == TransactionType.BANK_WITHDRAWAL) {
                    expenses = expenses.add(amount);
                } else if (type == TransactionType.REFUND) {
                    refunds = refunds.add(amount);
                } else if (isLoanRepayment(type)) {
                    principalRepaid = principalRepaid.add(principal.signum() > 0 ? principal : amount.subtract(interestComponent).max(BigDecimal.ZERO));
                    interest = interest.add(interestComponent);
                } else if (type == TransactionType.LOAN_INTEREST_PAYMENT || type == TransactionType.LOAN_INTEREST_INCOME || type == TransactionType.LOAN_INTEREST) {
                    interest = interest.add(amount);
                } else if (type == TransactionType.LOAN_PENALTY_INCOME || type == TransactionType.PENALTY_INCOME || type == TransactionType.SHARE_LATE_FEE || type == TransactionType.OTHER_INCOME) {
                    otherIncome = otherIncome.add(amount);
                }
            }
            // Member balances are the cumulative savings source of truth. A group
            // can contain historical collection records plus newer ledger entries;
            // using only the ledger here would replace (and hide) all historical
            // savings as soon as the first new payment is recorded.
            LegacyFundValues legacy = getLegacyFundValues(groupId);
            boolean savingsFromLegacy = legacy.savings().signum() > 0;
            if (savingsFromLegacy) {
                savings = legacy.savings();
            } else {
                // Keep support for imported groups whose member balances have not
                // yet been populated, while reserving refunds from ledger savings.
                savings = savings.subtract(refunds).max(BigDecimal.ZERO);
            }
            if (interest.signum() == 0) interest = legacy.interest();
            if (otherIncome.signum() == 0) otherIncome = legacy.otherIncome();
            if (expenses.signum() == 0) expenses = legacy.expenses();
            if (disbursed.signum() == 0) disbursed = legacy.disbursed();
            if (principalRepaid.signum() == 0) principalRepaid = legacy.principalRepaid();
            return new FundBreakdown(savings, interest, otherIncome, expenses, disbursed, principalRepaid, refunds);
        }

        // Compatibility path for legacy records which predate the central ledger.
        Group group = dataService.findGroupById(groupId).orElse(null);
        if (group == null) return new FundBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        LegacyFundValues legacy = getLegacyFundValues(groupId);
        return new FundBreakdown(legacy.savings(), legacy.interest(), legacy.otherIncome(),
                legacy.expenses(), legacy.disbursed(), legacy.principalRepaid(), BigDecimal.ZERO);
    }

    private LegacyFundValues getLegacyFundValues(String groupId) {
        BigDecimal savings = dataService.getMembersByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == null || m.getStatus() == MemberStatus.ACTIVE)
                .map(m -> nz(m.getTotalSavingsBalance())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal principalRepaid = dataService.getLoansByGroupId(groupId).stream()
                .map(l -> nz(l.getPrincipalPaid())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interest = dataService.getLoansByGroupId(groupId).stream()
                .map(l -> nz(l.getInterestPaid())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otherIncome = dataService.getIncomesByGroupId(groupId).stream()
                .filter(i -> !"LOAN_INTEREST".equalsIgnoreCase(i.getCategory()))
                .map(i -> nz(i.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenses = dataService.getExpensesByGroupId(groupId).stream()
                .map(e -> nz(e.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal disbursed = dataService.getLoansByGroupId(groupId).stream()
                .filter(l -> l.getStatus() != LoanStatus.PENDING && l.getStatus() != LoanStatus.APPROVED && l.getStatus() != LoanStatus.CANCELLED)
                .map(l -> nz(l.getPrincipalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LegacyFundValues(savings, interest, otherIncome, expenses, disbursed, principalRepaid);
    }

    private record LegacyFundValues(BigDecimal savings, BigDecimal interest, BigDecimal otherIncome,
                                    BigDecimal expenses, BigDecimal disbursed, BigDecimal principalRepaid) {}

    private boolean isLoanRepayment(TransactionType type) {
        return type == TransactionType.LOAN_REPAYMENT || type == TransactionType.LOAN_PRINCIPAL_PAYMENT
                || type == TransactionType.LOAN_PRINCIPAL_REPAYMENT || type == TransactionType.LOAN_EXTRA_PAYMENT
                || type == TransactionType.EXTRA_LOAN_PAYMENT;
    }

    public record FundBreakdown(BigDecimal savings, BigDecimal interest, BigDecimal otherIncome,
                                BigDecimal expenses, BigDecimal loansDisbursed,
                                BigDecimal principalRepaid, BigDecimal refunds) {
        public BigDecimal availableFund() {
            return savings.add(interest).add(otherIncome).add(principalRepaid)
                    .subtract(expenses).subtract(loansDisbursed);
        }
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
