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
                .filter(t -> isSuccessful(t) && countsTowardGroupFund(t)
                        && (before == null || (t.getDate() != null && t.getDate().isBefore(before))))
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
                // Suspension pauses participation; it does not remove the
                // member's accumulated savings from the group balance.
                .filter(m -> m.getStatus() == null || m.getStatus() != MemberStatus.EXITED)
                .map(m -> nz(m.getTotalSavingsBalance())).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Loan> loans = getUniqueLoans(groupId);
        BigDecimal principalRecovered = loans.stream()
                .map(l -> nz(l.getPrincipalPaid())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interest = loans.stream()
                .map(l -> nz(l.getInterestPaid())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal income = dataService.getIncomesByGroupId(groupId).stream()
                .filter(i -> !i.isDistributed())
                .map(i -> nz(i.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenses = dataService.getExpensesByGroupId(groupId).stream()
                .filter(e -> !e.isDistributed())
                .map(e -> nz(e.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal disbursed = loans.stream()
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
                .filter(t -> isSuccessful(t) && countsTowardGroupFund(t)).toList();
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
            // Some older loans were created before the central ledger entry was
            // introduced. Reconcile those loan records without double-counting
            // loans that already have a matching disbursement transaction.
            disbursed = reconcileDisbursedLoans(groupId, transactions, disbursed);
            // Keep historical member balances as a compatibility fallback, but do
            // not allow a stale member balance to hide newer verified collections.
            // This is important after a payment: the central ledger is updated
            // with the full amount while an older member balance may lag behind.
            LegacyFundValues legacy = getLegacyFundValues(groupId);
            if (legacy.savings().compareTo(savings) > 0) {
                savings = legacy.savings();
            } else {
                // Ledger savings include refund entries only when they are part
                // of the ledger total, so reserve those refunds in that path.
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
                // Suspended balances remain financially owned by the member;
                // only exited members are removed from this legacy aggregate.
                .filter(m -> m.getStatus() == null || m.getStatus() != MemberStatus.EXITED)
                .map(m -> nz(m.getTotalSavingsBalance())
                        .subtract(nz(m.getTotalOtherIncomeDistributed()))
                        .add(nz(m.getTotalOtherExpenseDistributed())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal principalRepaid = dataService.getLoansByGroupId(groupId).stream()
                .map(l -> nz(l.getPrincipalPaid())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interest = dataService.getLoansByGroupId(groupId).stream()
                .map(l -> nz(l.getInterestPaid())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otherIncome = dataService.getIncomesByGroupId(groupId).stream()
                .filter(i -> !"LOAN_INTEREST".equalsIgnoreCase(i.getCategory()))
                .filter(i -> !i.isDistributed())
                .map(i -> nz(i.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenses = dataService.getExpensesByGroupId(groupId).stream()
                .filter(e -> !e.isDistributed())
                .map(e -> nz(e.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal disbursed = dataService.getLoansByGroupId(groupId).stream()
                .filter(l -> l.getStatus() != LoanStatus.PENDING && l.getStatus() != LoanStatus.APPROVED && l.getStatus() != LoanStatus.CANCELLED)
                .map(l -> nz(l.getPrincipalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LegacyFundValues(savings, interest, otherIncome, expenses, disbursed, principalRepaid);
    }

    private BigDecimal reconcileDisbursedLoans(String groupId, List<Transaction> transactions,
                                               BigDecimal ledgerDisbursed) {
        Set<String> ledgerLoanIds = transactions.stream()
                .filter(t -> t.getType() == TransactionType.LOAN_DISBURSEMENT)
                .map(Transaction::getLoanId)
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());

        BigDecimal missingLegacyDisbursements = getUniqueLoans(groupId).stream()
                .filter(this::isDisbursedLoan)
                .filter(loan -> loan.getLoanId() == null || !ledgerLoanIds.contains(loan.getLoanId()))
                .map(loan -> nz(loan.getPrincipalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ledgerDisbursed.add(missingLegacyDisbursements);
    }

    private List<Loan> getUniqueLoans(String groupId) {
        return dataService.getLoansByGroupId(groupId).stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(
                        loan -> loan.getId() != null ? loan.getId() : loan.getLoanId(),
                        loan -> loan,
                        (first, duplicate) -> first))
                .values().stream().toList();
    }

    private boolean isDisbursedLoan(Loan loan) {
        if (loan == null || loan.getStatus() == null) return false;
        return loan.getStatus() != LoanStatus.PENDING
                && loan.getStatus() != LoanStatus.APPROVED
                && loan.getStatus() != LoanStatus.CANCELLED;
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

    /**
     * Internal distribution entries are member-level transfers, not new group
     * cash. The original income/expense transaction is also excluded once its
     * source record is marked distributed, preventing double counting.
     */
    private boolean countsTowardGroupFund(Transaction transaction) {
        if (transaction == null || transaction.getType() == null) return false;
        if (transaction.getType() == TransactionType.MEMBER_INCOME_DISTRIBUTION
                || transaction.getType() == TransactionType.MEMBER_EXPENSE_DISTRIBUTION) {
            return false;
        }
        if (transaction.getType() == TransactionType.OTHER_INCOME) {
            return !isDistributedIncome(transaction);
        }
        if (transaction.getType() == TransactionType.OTHER_EXPENSE) {
            return !isDistributedExpense(transaction);
        }
        return true;
    }

    private boolean isDistributedIncome(Transaction transaction) {
        return dataService.getIncomesByGroupId(transaction.getGroupId()).stream()
                .filter(i -> sameDateAndAmount(transaction.getDate(), transaction.getAmount(), i.getIncomeDate(), i.getAmount()))
                .filter(i -> transaction.getReference() == null || transaction.getReference().isBlank()
                        || transaction.getReference().equalsIgnoreCase(i.getId())
                        || transaction.getReference().equalsIgnoreCase(i.getReference()))
                .anyMatch(GroupIncome::isDistributed);
    }

    private boolean isDistributedExpense(Transaction transaction) {
        return dataService.getExpensesByGroupId(transaction.getGroupId()).stream()
                .filter(e -> sameDateAndAmount(transaction.getDate(), transaction.getAmount(), e.getExpenseDate(), e.getAmount()))
                .filter(e -> transaction.getReference() == null || transaction.getReference().isBlank()
                        || transaction.getReference().equalsIgnoreCase(e.getId())
                        || transaction.getReference().equalsIgnoreCase(e.getReference()))
                .anyMatch(GroupExpense::isDistributed);
    }

    private boolean sameDateAndAmount(LocalDate firstDate, BigDecimal firstAmount,
                                      LocalDate secondDate, BigDecimal secondAmount) {
        return java.util.Objects.equals(firstDate, secondDate)
                && nz(firstAmount).compareTo(nz(secondAmount)) == 0;
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
