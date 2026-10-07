package com.bachatgat.service;

import com.bachatgat.dto.DashboardAdminDTO;
import com.bachatgat.dto.DashboardUserDTO;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {

    private final FirestoreDataService dataService;
    private final GroupFundService groupFundService;

    public DashboardService(FirestoreDataService dataService, GroupFundService groupFundService) {
        this.dataService = dataService;
        this.groupFundService = groupFundService;
    }

    public DashboardAdminDTO getAdminDashboard(String groupId) {
        DashboardAdminDTO dto = new DashboardAdminDTO();
        List<Group> allGroups = dataService.getAllGroups();
        dto.setTotalGroups(allGroups.size());

        String targetGroupId = (groupId != null && !groupId.isEmpty()) ? groupId : (allGroups.isEmpty() ? null : allGroups.get(0).getId());

        List<Member> groupMembers = dataService.getMembersByGroupId(targetGroupId);
        long activeCount = groupMembers.stream().filter(m -> m.getStatus() == MemberStatus.ACTIVE).count();
        dto.setTotalActiveMembers((int) activeCount);

        // Monthly collections (Current month & year)
        int curMonth = LocalDate.now().getMonthValue();
        int curYear = LocalDate.now().getYear();

        List<CollectionRecord> collections = dataService.getCollectionsByGroupId(targetGroupId);
        List<CollectionRecord> currentMonthRecords = collections.stream()
                .filter(c -> c.getMonth() == curMonth && c.getYear() == curYear)
                .toList();

        BigDecimal expected = currentMonthRecords.stream()
                .map(CollectionRecord::getExpectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal collected = currentMonthRecords.stream()
                .map(CollectionRecord::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pending = currentMonthRecords.stream()
                .map(CollectionRecord::getPendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dto.setMonthlyExpectedCollection(expected);
        dto.setMonthlyCollected(collected);
        dto.setMonthlyPending(pending);

        if (expected.compareTo(BigDecimal.ZERO) > 0) {
            double pct = collected.doubleValue() / expected.doubleValue() * 100.0;
            dto.setCollectionPercentage(Math.round(pct * 10.0) / 10.0);
        }

        // Savings Aggregates
        BigDecimal totalSavings = groupMembers.stream()
                .map(m -> m.getTotalSavingsBalance() != null ? m.getTotalSavingsBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setTotalSavings(totalSavings);

        // Loans Aggregates
        List<Loan> groupLoans = dataService.getLoansByGroupId(targetGroupId);
        BigDecimal disbursed = groupLoans.stream()
                .filter(l -> l.getStatus() != LoanStatus.PENDING && l.getStatus() != LoanStatus.APPROVED && l.getStatus() != LoanStatus.CANCELLED)
                .map(l -> l.getPrincipalAmount() != null ? l.getPrincipalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal principalRecovered = groupLoans.stream()
                .map(l -> l.getPrincipalPaid() != null ? l.getPrincipalPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstandingPrincipal = groupLoans.stream()
                .map(l -> l.getOutstandingPrincipal() != null ? l.getOutstandingPrincipal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal interestCollected = groupLoans.stream()
                .map(l -> l.getInterestPaid() != null ? l.getInterestPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dto.setTotalLoansDisbursed(disbursed);
        dto.setTotalPrincipalRecovered(principalRecovered);
        dto.setTotalOutstandingLoans(outstandingPrincipal);
        dto.setTotalInterestCollected(interestCollected);

        // Other Income
        List<GroupIncome> incomes = dataService.getIncomesByGroupId(targetGroupId);
        BigDecimal totalOtherIncome = incomes.stream()
                .filter(inc -> !"LOAN_INTEREST".equalsIgnoreCase(inc.getCategory()))
                .map(inc -> inc.getAmount() != null ? inc.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setTotalOtherIncome(totalOtherIncome);

        // Expenses
        List<GroupExpense> expenses = dataService.getExpensesByGroupId(targetGroupId);
        BigDecimal totalExpenses = expenses.stream()
                .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setTotalExpenses(totalExpenses);

        // Group Fund Transparency & Available Cash Calculation (Sections 11 & 14):
        // Available Fund = Savings + Principal Recovered + Loan Interest + Other Income - Disbursed Loans - Expenses
        BigDecimal availableCash = groupFundService.getAvailableFund(targetGroupId);
        dto.setGroupAvailableFund(availableCash);
        dto.setTotalGroupCorpus(availableCash.add(outstandingPrincipal));
        dto.setBankBalance(groupFundService.getBalanceByPaymentMethod(targetGroupId, true));
        dto.setCashBalance(groupFundService.getBalanceByPaymentMethod(targetGroupId, false));

        // Loan Application pending counts
        List<LoanApplication> apps = dataService.getLoanApplicationsByGroupId(targetGroupId);
        long pendingApps = apps.stream().filter(a -> a.getStatus() == LoanApplicationStatus.PENDING).count();
        dto.setPendingLoanApplicationsCount((int) pendingApps);

        long approvedLoans = groupLoans.stream().filter(l -> l.getStatus() == LoanStatus.APPROVED).count();
        long activeLoans = groupLoans.stream().filter(l -> l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.PARTIALLY_PAID).count();
        long overdueLoans = groupLoans.stream().filter(l -> l.getStatus() == LoanStatus.OVERDUE).count();
        dto.setApprovedLoansCount((int) approvedLoans);
        dto.setActiveLoansCount((int) activeLoans);
        dto.setOverdueLoansCount((int) overdueLoans);

        // Recent transactions
        List<Transaction> txns = dataService.getTransactionsByGroupId(targetGroupId);
        dto.setRecentTransactions(txns.stream().limit(10).toList());

        return dto;
    }

    public DashboardUserDTO getUserDashboard(String memberId) {
        Member member = dataService.findMemberByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for: " + memberId));

        DashboardUserDTO dto = new DashboardUserDTO();
        dto.setMemberName(member.getFullName());
        dto.setMemberId(member.getMemberId());
        dto.setGroupName(member.getGroupName());
        dto.setMembershipStatus(member.getStatus().name());

        Group group = dataService.findGroupById(member.getGroupId()).orElse(null);
        if (group != null) {
            dto.setRegistrationId(group.getRegistrationId());

            LocalDate today = LocalDate.now();
            GroupMasterData currentRules = dataService.getMasterDataForDate(group.getId(), today).orElse(null);
            BigDecimal monthlyBachat = currentRules != null && currentRules.getMonthlyBachatAmount() != null
                    ? currentRules.getMonthlyBachatAmount()
                    : (group.getMonthlyBachatAmount() != null ? group.getMonthlyBachatAmount() : BigDecimal.ZERO);
            dto.setMonthlyBachatAmount(monthlyBachat);
            dto.setPendingCollection(monthlyBachat);

            String currentBusinessKey = group.getId() + "_" + member.getMemberId() + "_"
                    + today.getMonthValue() + "_" + today.getYear();
            dataService.findCollectionByBusinessKey(currentBusinessKey).ifPresent(currentCollection -> {
                if (currentCollection.getExpectedAmount() != null) {
                    dto.setMonthlyBachatAmount(currentCollection.getExpectedAmount());
                }
                BigDecimal expected = currentCollection.getExpectedAmount() != null
                        ? currentCollection.getExpectedAmount() : BigDecimal.ZERO;
                BigDecimal paid = currentCollection.getPaidAmount() != null
                        ? currentCollection.getPaidAmount() : BigDecimal.ZERO;
                dto.setPendingCollection(expected.subtract(paid).max(BigDecimal.ZERO));
            });

            List<Member> groupMembers = dataService.getMembersByGroupId(group.getId());
            BigDecimal totalSavings = groupMembers.stream()
                    .map(m -> m.getTotalSavingsBalance() != null ? m.getTotalSavingsBalance() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<Loan> groupLoans = dataService.getLoansByGroupId(group.getId());
            BigDecimal disbursed = groupLoans.stream()
                    .filter(l -> l.getStatus() != LoanStatus.PENDING && l.getStatus() != LoanStatus.APPROVED && l.getStatus() != LoanStatus.CANCELLED)
                    .map(l -> l.getPrincipalAmount() != null ? l.getPrincipalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal principalRecovered = groupLoans.stream()
                    .map(l -> l.getPrincipalPaid() != null ? l.getPrincipalPaid() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal interestCollected = groupLoans.stream()
                    .map(l -> l.getInterestPaid() != null ? l.getInterestPaid() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<GroupIncome> incomes = dataService.getIncomesByGroupId(group.getId());
            BigDecimal totalOtherIncome = incomes.stream()
                    .filter(inc -> !"LOAN_INTEREST".equalsIgnoreCase(inc.getCategory()))
                    .map(inc -> inc.getAmount() != null ? inc.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<GroupExpense> expenses = dataService.getExpensesByGroupId(group.getId());
            BigDecimal totalExpenses = expenses.stream()
                    .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal availableCash = groupFundService.getAvailableFund(group.getId());

            dto.setGroupRemainingFunds(availableCash);
            dto.setGroupLoansDisbursed(disbursed);
        }

        dto.setMyTotalSavings(member.getTotalSavingsBalance() != null ? member.getTotalSavingsBalance() : BigDecimal.ZERO);
        dto.setMyCurrentShare(member.getCurrentShareBalance() != null ? member.getCurrentShareBalance() : BigDecimal.ZERO);
        dto.setMyIncome(member.getMyIncome());
        dto.setMyPenalties(member.getPendingLateFees() != null ? member.getPendingLateFees() : BigDecimal.ZERO);
        dto.setNetFinancialPosition(member.getTotalFinancialPosition());

        // Fetch active loan
        dataService.findActiveLoanByMemberId(member.getMemberId()).ifPresent(loan -> {
            dto.setActiveLoanId(loan.getLoanId());
            dto.setMyLoanAmount(loan.getPrincipalAmount());
            dto.setMyLoanOutstanding(loan.getOutstandingPrincipal() != null ? loan.getOutstandingPrincipal() : loan.getTotalOutstanding());
            dto.setMyInterestRate(loan.getInterestRate());
            dto.setTotalInterestPaid(loan.getInterestPaid());
            dto.setNextPaymentAmount(loan.getMonthlyInstallment());
            dto.setNextPaymentDueDate(loan.getNextDueDate());
            dto.setActiveLoan(loan);
        });

        // Member's own transactions strictly
        List<Transaction> memberTxns = dataService.getTransactionsByMemberId(member.getMemberId());
        dto.setRecentTransactions(memberTxns.stream().limit(10).toList());

        return dto;
    }
}
