package com.bachatgat.service;

import com.bachatgat.model.Group;
import com.bachatgat.model.GroupExpense;
import com.bachatgat.model.GroupIncome;
import com.bachatgat.model.Loan;
import com.bachatgat.model.LoanStatus;
import com.bachatgat.model.Member;
import com.bachatgat.model.MemberStatus;
import com.bachatgat.model.Transaction;
import com.bachatgat.model.TransactionType;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
            TransactionType.PENALTY_INCOME,
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

    BigDecimal getCurrentLendingCapacity(String groupId) {
        return getFundBreakdown(groupId, dataService.getGroupTransactionsForLending(groupId)).availableFund().max(BigDecimal.ZERO);
    }

    public BigDecimal getAvailableFundBefore(String groupId, LocalDate date) {
        if (date == null) return getAvailableFund(groupId);
        List<Transaction> ledger = dataService.getTransactionsByGroupId(groupId);
        return ledger.stream()
                .filter(t -> t.getDate() != null && t.getDate().isBefore(date))
                .filter(this::isSuccessful).filter(this::countsTowardGroupFund)
                .map(this::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(unpostedOtherIncome(groupId, ledger, date));
    }

    /**
     * Single source of truth for the dashboard accounting breakdown. Every
     * displayed component and the final available fund are derived from the
     * same successful ledger entries, so the numbers cannot drift apart.
     */
    public FundBreakdown getFundBreakdown(String groupId) {
        return getFundBreakdown(groupId, dataService.getTransactionsByGroupId(groupId));
    }

    FundBreakdown getFundBreakdown(String groupId, List<Transaction> ledger) {
        List<Transaction> transactions = ledger.stream()
                .filter(t -> isSuccessful(t) && countsTowardGroupFund(t)).toList();
        BigDecimal unpostedIncome = unpostedOtherIncome(groupId, ledger, null);
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
                } else if (isOtherIncome(type)) {
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
            otherIncome = otherIncome.add(unpostedIncome);
            // Expenses without a matching cash transaction predate the ledger.
            expenses = expenses.add(unpostedExpenses(groupId, transactions));
            if (disbursed.signum() == 0) disbursed = legacy.disbursed();
            if (principalRepaid.signum() == 0) principalRepaid = legacy.principalRepaid();
            return new FundBreakdown(savings, interest, otherIncome, expenses, disbursed, principalRepaid, refunds);
        }

        // Compatibility path for legacy records which predate the central ledger.
        Group group = dataService.findGroupById(groupId).orElse(null);
        if (group == null && unpostedIncome.signum() == 0) return new FundBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        LegacyFundValues legacy = getLegacyFundValues(groupId);
        return new FundBreakdown(legacy.savings(), legacy.interest(), unpostedIncome,
                legacy.expenses(), legacy.disbursed(), legacy.principalRepaid(), BigDecimal.ZERO);
    }

    private LegacyFundValues getLegacyFundValues(String groupId) {
        BigDecimal savings = dataService.getMembersByGroupIdIncludingArchived(groupId).stream()
                // Suspended balances remain financially owned by the member;
                // only exited members are removed from this legacy aggregate.
                .filter(m -> m.getStatus() == null || m.getStatus() != MemberStatus.EXITED)
                .map(Member::bachatCapitalBeforeExpenses)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal principalRepaid = dataService.getLoansByGroupId(groupId).stream()
                .map(l -> nz(l.getPrincipalPaid())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interest = dataService.getLoansByGroupId(groupId).stream()
                .map(l -> nz(l.getInterestPaid())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenses = dataService.getExpensesByGroupId(groupId).stream()
                .map(e -> nz(e.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal disbursed = dataService.getLoansByGroupId(groupId).stream()
                .filter(l -> l.getStatus() != LoanStatus.PENDING && l.getStatus() != LoanStatus.APPROVED && l.getStatus() != LoanStatus.CANCELLED)
                .map(l -> nz(l.getTopUpAmount() != null ? l.getTopUpAmount() : l.getPrincipalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LegacyFundValues(savings, interest, expenses, disbursed, principalRepaid);
    }

    private BigDecimal unpostedOtherIncome(String groupId, List<Transaction> ledger, LocalDate before) {
        // Match each receipt to at most one older income record, including failed
        // receipts so a recorded failure cannot reappear as a legacy credit.
        List<Transaction> receipts = new ArrayList<>(ledger.stream().filter(t -> isOtherIncome(t.getType())).toList());
        BigDecimal total = BigDecimal.ZERO;
        for (GroupIncome income : dataService.getIncomesByGroupId(groupId)) {
            if ("LOAN_INTEREST".equalsIgnoreCase(income.getCategory())) continue;
            Transaction receipt = receipts.stream().filter(t -> matchesIncome(t, income)).findFirst().orElse(null);
            if (receipt != null) {
                receipts.remove(receipt);
            } else if (before == null || income.getIncomeDate() != null && income.getIncomeDate().isBefore(before)) {
                total = total.add(nz(income.getAmount()));
            }
        }
        return total;
    }

    private boolean matchesIncome(Transaction transaction, GroupIncome income) {
        String reference = transaction.getReference();
        boolean sameReference = reference == null || reference.isBlank()
                ? income.getReference() == null || income.getReference().isBlank()
                : reference.equalsIgnoreCase(income.getId()) || reference.equalsIgnoreCase(income.getReference());
        return sameReference
                && (transaction.getType() == TransactionType.OTHER_INCOME || "PENALTY".equalsIgnoreCase(income.getCategory()))
                && sameDateAndAmount(transaction.getDate(), transaction.getAmount(), income.getIncomeDate(), income.getAmount());
    }

    private boolean isOtherIncome(TransactionType type) {
        return type == TransactionType.OTHER_INCOME || type == TransactionType.PENALTY_INCOME
                || type == TransactionType.LOAN_PENALTY_INCOME || type == TransactionType.SHARE_LATE_FEE;
    }

    private BigDecimal unpostedExpenses(String groupId, List<Transaction> transactions) {
        List<Transaction> cashExpenses = transactions.stream()
                .filter(t -> t.getType() == TransactionType.OTHER_EXPENSE || t.getType() == TransactionType.EXPENSE)
                .toList();
        return dataService.getExpensesByGroupId(groupId).stream()
                .filter(expense -> cashExpenses.stream().noneMatch(t -> matchesExpense(t, expense)))
                .map(expense -> nz(expense.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
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
                .map(loan -> nz(loan.getTopUpAmount() != null ? loan.getTopUpAmount() : loan.getPrincipalAmount()))
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

    private record LegacyFundValues(BigDecimal savings, BigDecimal interest,
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
        return getBalanceByPaymentMethod(dataService.getTransactionsByGroupId(groupId), bank);
    }

    BigDecimal getBalanceByPaymentMethod(List<Transaction> ledger, boolean bank) {
        return ledger.stream()
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
     * Internal distribution entries adjust member balances. The original
     * expense remains the cash debit after its member allocations are posted.
     */
    private boolean countsTowardGroupFund(Transaction transaction) {
        if (transaction == null || transaction.getType() == null) return false;
        if (transaction.getType() == TransactionType.MEMBER_INCOME_DISTRIBUTION
                || transaction.getType() == TransactionType.MEMBER_EXPENSE_DISTRIBUTION) {
            return false;
        }
        return true;
    }

    private boolean matchesExpense(Transaction transaction, GroupExpense expense) {
        String reference = transaction.getReference();
        return sameDateAndAmount(transaction.getDate(), transaction.getAmount(), expense.getExpenseDate(), expense.getAmount())
                && (reference == null || reference.isBlank() || reference.equalsIgnoreCase(expense.getId())
                        || reference.equalsIgnoreCase(expense.getReference()));
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
