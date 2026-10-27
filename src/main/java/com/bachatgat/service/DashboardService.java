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

        GroupFundService.FundBreakdown fund = groupFundService.getFundBreakdown(targetGroupId);
        dto.setTotalSavings(fund.savings());

        // Loans Aggregates
        List<Loan> groupLoans = dataService.getLoansByGroupId(targetGroupId);
        BigDecimal disbursed = fund.loansDisbursed();
        BigDecimal principalRecovered = fund.principalRepaid();

        BigDecimal outstandingPrincipal = groupLoans.stream()
                .map(l -> l.getOutstandingPrincipal() != null ? l.getOutstandingPrincipal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal interestCollected = fund.interest();

        dto.setTotalLoansDisbursed(disbursed);
        dto.setTotalPrincipalRecovered(principalRecovered);
        dto.setTotalOutstandingLoans(outstandingPrincipal);
        dto.setTotalInterestCollected(interestCollected);

        dto.setTotalOtherIncome(fund.otherIncome());
        dto.setTotalExpenses(fund.expenses());

        // Group Fund Transparency & Available Cash Calculation (Sections 11 & 14):
        // Available Fund = Savings + Principal Recovered + Loan Interest + Other Income - Disbursed Loans - Expenses
        BigDecimal availableCash = fund.availableFund();
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
            GroupFundService.FundBreakdown fund = groupFundService.getFundBreakdown(group.getId());
            BigDecimal availableCash = fund.availableFund();

            dto.setGroupRemainingFunds(availableCash);
            dto.setGroupLoansDisbursed(fund.loansDisbursed());
        }

        dto.setMyTotalSavings(member.getTotalSavingsBalance() != null ? member.getTotalSavingsBalance() : BigDecimal.ZERO);
        dto.setMyCurrentShare(member.getCurrentShareBalance() != null ? member.getCurrentShareBalance() : BigDecimal.ZERO);
        dto.setMyIncome(member.getMyIncome());
        // Recalculate the open current-month penalty from the collection record
        // so checkout cannot use a stale member snapshot after the due date.
        BigDecimal currentPenalty = member.getPendingLateFees() != null ? member.getPendingLateFees() : BigDecimal.ZERO;
        if (group != null) {
            GroupMasterData penaltyRules = dataService.getMasterDataForDate(group.getId(), LocalDate.now()).orElse(null);
            int month = LocalDate.now().getMonthValue();
            int year = LocalDate.now().getYear();
            String key = group.getId() + "_" + member.getMemberId() + "_" + month + "_" + year;
            CollectionRecord current = dataService.findCollectionByBusinessKey(key).orElse(null);
            if (current != null && current.getDueDate() != null
                    && (current.getPaidAmount() == null || current.getPaidAmount().compareTo(current.getExpectedAmount() != null ? current.getExpectedAmount() : BigDecimal.ZERO) < 0)
                    && LocalDate.now().isAfter(current.getDueDate())) {
                BigDecimal dailyFee = penaltyRules != null && penaltyRules.getLatePaymentFee() != null
                        ? penaltyRules.getLatePaymentFee()
                        : (group.getLateFeeAmount() != null ? group.getLateFeeAmount() : BigDecimal.ZERO);
                long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(current.getDueDate(), LocalDate.now());
                BigDecimal alreadyPaid = current.getPaidLateFee() != null ? current.getPaidLateFee() : BigDecimal.ZERO;
                currentPenalty = dailyFee.multiply(BigDecimal.valueOf(overdueDays)).subtract(alreadyPaid).max(BigDecimal.ZERO);
            } else if (current != null && (current.getPaidAmount() == null || current.getPaidAmount().signum() == 0)) {
                currentPenalty = BigDecimal.ZERO;
            }
        }
        dto.setMyPenalties(currentPenalty);
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
            List<LoanRepaymentSchedule> remainingSchedule = dataService.getLoanSchedule(loan.getId()).stream()
                    .filter(item -> item.getStatus() != RepaymentStatus.PAID)
                    .limit(2)
                    .toList();
            if (!remainingSchedule.isEmpty()) {
                LoanRepaymentSchedule current = remainingSchedule.get(0);
                BigDecimal paid = nz(current.getPaidAmount());
                BigDecimal scheduledInterest = nz(current.getInterestAmount());
                BigDecimal interestDue = scheduledInterest.subtract(paid.min(scheduledInterest)).max(BigDecimal.ZERO);
                BigDecimal principalAlreadyPaid = paid.subtract(scheduledInterest).max(BigDecimal.ZERO);
                BigDecimal principalDue = nz(current.getPrincipalAmount()).subtract(principalAlreadyPaid).max(BigDecimal.ZERO);
                BigDecimal currentDue = interestDue.add(principalDue);
                dto.setCurrentEmi(currentDue);
                dto.setCurrentPrincipal(principalDue);
                dto.setCurrentInterest(interestDue);
                dto.setNextPaymentAmount(currentDue);
                dto.setNextPaymentDueDate(current.getDueDate());
                dto.setRemainingTenure(dataService.getLoanSchedule(loan.getId()).stream()
                        .filter(item -> item.getStatus() != RepaymentStatus.PAID).toList().size());
            }
            if (remainingSchedule.size() > 1) {
                LoanRepaymentSchedule next = remainingSchedule.get(1);
                dto.setNextEmi(nz(next.getTotalDue()));
                dto.setNextPrincipal(nz(next.getPrincipalAmount()));
                dto.setNextInterest(nz(next.getInterestAmount()));
            }
            dto.setActiveLoan(loan);
        });

        // Member's own transactions strictly
        List<Transaction> memberTxns = dataService.getTransactionsByMemberId(member.getMemberId());
        dto.setRecentTransactions(memberTxns.stream().limit(10).toList());

        return dto;
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
