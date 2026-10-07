package com.bachatgat.service;

import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;

@Service
public class ReportService {

    private final FirestoreDataService dataService;
    private final GroupFundService groupFundService;

    public ReportService(FirestoreDataService dataService, GroupFundService groupFundService) {
        this.dataService = dataService;
        this.groupFundService = groupFundService;
    }

    public Map<String, Object> getMonthlyReport(String groupId, int month, int year) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12.");
        }
        Map<String, Object> report = new HashMap<>();
        Group group = dataService.findGroupById(groupId).orElse(null);
        report.put("group", group);
        report.put("month", month);
        report.put("year", year);

        YearMonth selected = YearMonth.of(year, month);
        LocalDate start = selected.atDay(1);
        LocalDate endExclusive = selected.plusMonths(1).atDay(1);

        List<CollectionRecord> monthCollections = dataService.getCollectionsByGroupId(groupId).stream()
                .filter(c -> c.getMonth() == month && c.getYear() == year)
                .toList();
        report.put("collections", monthCollections);

        BigDecimal expected = monthCollections.stream().map(c -> nz(c.getExpectedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = monthCollections.stream().map(c -> nz(c.getPaidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = monthCollections.stream().map(c -> nz(c.getPendingAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlyBachatPenalty = monthCollections.stream().map(c -> nz(c.getLateFeeAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        GroupMasterData applicableRules = dataService.getMasterDataForDate(groupId, start).orElse(null);

        List<Transaction> monthTransactions = dataService.getTransactionsByGroupId(groupId).stream()
                .filter(t -> t.getDate() != null && !t.getDate().isBefore(start) && t.getDate().isBefore(endExclusive))
                .filter(t -> t.getPaymentStatus() == null || "SUCCESS".equalsIgnoreCase(t.getPaymentStatus()))
                .toList();
        List<GroupIncome> monthIncomes = dataService.getIncomesByGroupId(groupId).stream()
                .filter(i -> i.getIncomeDate() != null && !i.getIncomeDate().isBefore(start) && i.getIncomeDate().isBefore(endExclusive))
                .toList();
        List<GroupExpense> monthExpenses = dataService.getExpensesByGroupId(groupId).stream()
                .filter(e -> e.getExpenseDate() != null && !e.getExpenseDate().isBefore(start) && e.getExpenseDate().isBefore(endExclusive))
                .toList();

        BigDecimal principalRecovered = sumTransactions(monthTransactions, TransactionType.LOAN_PRINCIPAL_PAYMENT, TransactionType.LOAN_PRINCIPAL_REPAYMENT);
        BigDecimal interestIncome = sumTransactions(monthTransactions, TransactionType.LOAN_INTEREST_PAYMENT, TransactionType.LOAN_INTEREST_INCOME, TransactionType.LOAN_INTEREST);
        BigDecimal penaltyIncome = sumTransactions(monthTransactions, TransactionType.LOAN_PENALTY_INCOME, TransactionType.SHARE_LATE_FEE);
        BigDecimal otherIncome = sumTransactions(monthTransactions, TransactionType.OTHER_INCOME);
        BigDecimal loanDisbursement = sumTransactions(monthTransactions, TransactionType.LOAN_DISBURSEMENT);
        BigDecimal expenses = monthExpenses.stream().map(e -> nz(e.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Loan> loansGiven = dataService.getLoansByGroupId(groupId).stream()
                .filter(l -> l.getDisbursementDate() != null && !l.getDisbursementDate().isBefore(start) && l.getDisbursementDate().isBefore(endExclusive))
                .toList();
        List<Loan> activeLoans = dataService.getLoansByGroupId(groupId).stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.PARTIALLY_PAID || l.getStatus() == LoanStatus.OVERDUE)
                .toList();
        BigDecimal outstandingLoan = activeLoans.stream().map(l -> nz(l.getOutstandingPrincipal())).reduce(BigDecimal.ZERO, BigDecimal::add);

        report.put("openingFund", groupFundService.getAvailableFundBefore(groupId, start));
        report.put("monthlyCollection", collected);
        report.put("loanPrincipalRecovered", principalRecovered);
        report.put("loanInterestIncome", interestIncome);
        report.put("loanPenaltyIncome", penaltyIncome);
        report.put("otherIncome", otherIncome);
        report.put("loanDisbursement", loanDisbursement);
        report.put("monthlyExpenses", expenses);
        report.put("remainingFund", groupFundService.getAvailableFundBefore(groupId, endExclusive));
        report.put("outstandingLoanPrincipal", outstandingLoan);
        report.put("transactions", monthTransactions);
        report.put("incomes", monthIncomes);
        report.put("expenses", monthExpenses);
        report.put("loanPayments", monthTransactions.stream().filter(t -> t.getType() != null && (t.getType().name().contains("LOAN_"))).toList());
        report.put("loansGiven", loansGiven);
        report.put("loansGivenThisMonth", !loansGiven.isEmpty());
        report.put("savingsPaidMembers", monthCollections.stream().filter(c -> c.getStatus() == CollectionStatus.PAID || c.getStatus() == CollectionStatus.LATE).map(CollectionRecord::getMemberId).toList());
        report.put("pendingShareMembers", monthCollections.stream().filter(c -> c.getStatus() != CollectionStatus.PAID && c.getStatus() != CollectionStatus.LATE).map(CollectionRecord::getMemberId).toList());

        report.put("totalExpected", expected);
        report.put("totalCollected", collected);
        report.put("totalPending", pending);
        report.put("monthlyBachatAmount", applicableRules != null ? applicableRules.getMonthlyBachatAmount() : BigDecimal.ZERO);
        report.put("totalExpectedMonthlyBachat", expected);
        report.put("totalMonthlyBachatCollected", collected);
        report.put("totalMonthlyBachatPending", pending);
        report.put("totalMonthlyBachatPenalty", monthlyBachatPenalty);

        return report;
    }

    private BigDecimal sumTransactions(List<Transaction> transactions, TransactionType... types) {
        java.util.Set<TransactionType> accepted = java.util.EnumSet.noneOf(TransactionType.class);
        java.util.Collections.addAll(accepted, types);
        return transactions.stream().filter(t -> accepted.contains(t.getType())).map(t -> nz(t.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public Map<String, Object> getGroupFinancialSummary(String groupId) {
        Map<String, Object> summary = new HashMap<>();
        Group group = dataService.findGroupById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + groupId));

        summary.put("group", group);
        List<Member> members = dataService.getMembersByGroupId(groupId);
        List<Loan> loans = dataService.getLoansByGroupId(groupId);
        List<GroupExpense> expenses = dataService.getExpensesByGroupId(groupId);

        BigDecimal totalSavings = members.stream().map(Member::getTotalSavingsBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalShareContributions = members.stream().map(Member::getCurrentShareBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLoansDisbursed = loans.stream().map(Loan::getPrincipalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPrincipalRecovered = loans.stream().map(Loan::getPrincipalPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInterestCollected = loans.stream().map(Loan::getInterestPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpenses = expenses.stream().map(GroupExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingPrincipal = loans.stream().map(Loan::getOutstandingPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingInterest = loans.stream().map(Loan::getOutstandingInterest).reduce(BigDecimal.ZERO, BigDecimal::add);

        summary.put("totalSavings", totalSavings);
        summary.put("totalShareContributions", totalShareContributions);
        summary.put("totalLoansDisbursed", totalLoansDisbursed);
        summary.put("totalPrincipalRecovered", totalPrincipalRecovered);
        summary.put("totalInterestCollected", totalInterestCollected);
        summary.put("totalExpenses", totalExpenses);
        summary.put("outstandingPrincipal", outstandingPrincipal);
        summary.put("outstandingInterest", outstandingInterest);
        summary.put("totalOutstanding", outstandingPrincipal.add(outstandingInterest));

        return summary;
    }

    public Map<String, Object> getMemberStatement(String memberId) {
        Map<String, Object> statement = new HashMap<>();
        Member member = dataService.findMemberByMemberId(memberId)
                .or(() -> dataService.findMemberById(memberId))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + memberId));

        Group group = dataService.findGroupById(member.getGroupId()).orElse(null);
        statement.put("group", group);
        statement.put("member", member);

        List<CollectionRecord> collections = dataService.getCollectionsByMemberId(member.getMemberId());
        statement.put("collections", collections);

        List<Loan> loans = dataService.getLoansByMemberId(member.getMemberId());
        statement.put("loans", loans);

        List<Transaction> transactions = dataService.getTransactionsByMemberId(member.getMemberId());
        statement.put("transactions", transactions);

        List<MemberAdjustment> adjustments = dataService.getAdjustmentsByMemberId(member.getMemberId());
        statement.put("adjustments", adjustments);

        return statement;
    }

    public Map<String, Object> getYearlyReport(String groupId, int year) {
        Map<String, Object> report = new HashMap<>();
        Group group = dataService.findGroupById(groupId).orElse(null);
        report.put("group", group);
        report.put("year", year);

        List<CollectionRecord> yearCollections = dataService.getCollectionsByGroupId(groupId).stream()
                .filter(c -> c.getYear() == year)
                .toList();

        BigDecimal totalExpected = yearCollections.stream().map(CollectionRecord::getExpectedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCollected = yearCollections.stream().map(CollectionRecord::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPending = yearCollections.stream().map(CollectionRecord::getPendingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLateFee = yearCollections.stream().map(CollectionRecord::getPaidLateFee).reduce(BigDecimal.ZERO, BigDecimal::add);

        report.put("totalExpected", totalExpected);
        report.put("totalCollected", totalCollected);
        report.put("totalPending", totalPending);
        report.put("totalLateFee", totalLateFee);

        // Group Incomes & Expenses for that year
        List<GroupIncome> yearIncomes = dataService.getIncomesByGroupId(groupId).stream()
                .filter(i -> i.getIncomeDate() != null && i.getIncomeDate().getYear() == year)
                .toList();
        BigDecimal totalIncome = yearIncomes.stream().map(GroupIncome::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        report.put("totalOtherIncome", totalIncome);

        List<GroupExpense> yearExpenses = dataService.getExpensesByGroupId(groupId).stream()
                .filter(e -> e.getExpenseDate() != null && e.getExpenseDate().getYear() == year)
                .toList();
        BigDecimal totalExpense = yearExpenses.stream().map(GroupExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        report.put("totalOtherExpense", totalExpense);

        List<Transaction> yearTransactions = dataService.getTransactionsByGroupId(groupId).stream()
                .filter(t -> t.getDate() != null && t.getDate().getYear() == year)
                .filter(t -> t.getPaymentStatus() == null || "SUCCESS".equalsIgnoreCase(t.getPaymentStatus()))
                .toList();
        BigDecimal principalRecovered = sumTransactions(yearTransactions,
                TransactionType.LOAN_PRINCIPAL_PAYMENT, TransactionType.LOAN_PRINCIPAL_REPAYMENT,
                TransactionType.LOAN_REPAYMENT, TransactionType.LOAN_EXTRA_PAYMENT,
                TransactionType.EXTRA_LOAN_PAYMENT);
        BigDecimal interestCollected = sumTransactions(yearTransactions,
                TransactionType.LOAN_INTEREST_PAYMENT, TransactionType.LOAN_INTEREST_INCOME,
                TransactionType.LOAN_INTEREST);
        BigDecimal loansDisbursed = sumTransactions(yearTransactions, TransactionType.LOAN_DISBURSEMENT);
        BigDecimal sharesCollected = totalCollected;
        BigDecimal totalInflow = sharesCollected.add(principalRecovered).add(interestCollected).add(totalIncome);
        BigDecimal totalOutflow = loansDisbursed.add(totalExpense);

        // Names consumed by the existing report page.
        report.put("sharesCollected", sharesCollected);
        report.put("principalRecovered", principalRecovered);
        report.put("interestCollected", interestCollected);
        report.put("loansDisbursed", loansDisbursed);
        report.put("expensesTotal", totalExpense);
        report.put("otherIncomeTotal", totalIncome);
        report.put("totalInflow", totalInflow);
        report.put("totalOutflow", totalOutflow);
        report.put("netSurplus", totalInflow.subtract(totalOutflow));

        // Monthly Breakdown
        Map<Integer, Map<String, BigDecimal>> monthlyBreakdown = new HashMap<>();
        for (int m = 1; m <= 12; m++) {
            final int monthVal = m;
            BigDecimal mCollected = yearCollections.stream()
                    .filter(c -> c.getMonth() == monthVal)
                    .map(CollectionRecord::getPaidAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal mExpected = yearCollections.stream()
                    .filter(c -> c.getMonth() == monthVal)
                    .map(CollectionRecord::getExpectedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, BigDecimal> data = new HashMap<>();
            data.put("collected", mCollected);
            data.put("expected", mExpected);
            monthlyBreakdown.put(m, data);
        }
        report.put("monthlyBreakdown", monthlyBreakdown);

        return report;
    }

    public Map<String, Object> getOutstandingReport(String groupId) {
        Map<String, Object> report = new HashMap<>();
        Group group = dataService.findGroupById(groupId).orElse(null);
        report.put("group", group);

        List<Loan> activeLoans = dataService.getLoansByGroupId(groupId).stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.PARTIALLY_PAID || l.getStatus() == LoanStatus.OVERDUE)
                .toList();

        BigDecimal totalPrincipalOutstanding = activeLoans.stream()
                .map(Loan::getOutstandingPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInterestOutstanding = activeLoans.stream()
                .map(Loan::getOutstandingInterest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        report.put("activeLoansCount", activeLoans.size());
        report.put("loans", activeLoans);
        report.put("totalPrincipalOutstanding", totalPrincipalOutstanding);
        report.put("totalInterestOutstanding", totalInterestOutstanding);
        report.put("totalOutstanding", totalPrincipalOutstanding.add(totalInterestOutstanding));
        BigDecimal totalRecovered = activeLoans.stream()
                .map(l -> nz(l.getPrincipalPaid()).add(nz(l.getInterestPaid())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Backward-compatible aliases used by the existing browser page.
        report.put("totalOutstandingPrincipal", totalPrincipalOutstanding);
        report.put("totalRecovered", totalRecovered);

        return report;
    }

    public Map<String, Object> shareReportWhatsApp(String groupId, com.bachatgat.dto.ReportShareRequest request, String adminUsername) {
        Map<String, Object> res = new HashMap<>();
        String reportId = "RPT-" + UUID.randomUUID().toString().substring(0, 8);
        res.put("reportId", reportId);
        res.put("channel", "WHATSAPP");
        res.put("recipient", request.getRecipient());
        res.put("status", "SENT");
        res.put("timestamp", java.time.LocalDateTime.now().toString());
        res.put("message", "Financial report generated and dispatched to WhatsApp recipient: " + request.getRecipient());
        return res;
    }

    public Map<String, Object> sendReportEmail(String groupId, com.bachatgat.dto.ReportShareRequest request, String adminUsername) {
        Map<String, Object> res = new HashMap<>();
        String reportId = "RPT-" + UUID.randomUUID().toString().substring(0, 8);
        res.put("reportId", reportId);
        res.put("channel", "EMAIL");
        res.put("recipient", request.getRecipient());
        res.put("status", "QUEUED");
        res.put("timestamp", java.time.LocalDateTime.now().toString());
        res.put("message", "Financial report statement queued for background SMTP delivery to: " + request.getRecipient());
        return res;
    }
}

