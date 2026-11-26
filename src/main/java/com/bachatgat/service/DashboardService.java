package com.bachatgat.service;

import com.bachatgat.dto.DashboardAdminDTO;
import com.bachatgat.dto.DashboardUserDTO;
import com.bachatgat.exception.ResourceNotFoundException;
import com.bachatgat.model.*;
import com.bachatgat.repository.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final FirestoreDataService dataService;
    private final GroupFundService groupFundService;

    private final CollectionService collectionService;
    private final MemberService memberService;

    public DashboardService(FirestoreDataService dataService, GroupFundService groupFundService,
                            CollectionService collectionService, MemberService memberService) {
        this.dataService = dataService;
        this.groupFundService = groupFundService;
        this.collectionService = collectionService;
        this.memberService = memberService;
    }

    public DashboardAdminDTO getAdminDashboard(String groupId) {
        DashboardAdminDTO dto = new DashboardAdminDTO();
        List<Group> allGroups = dataService.getAllGroups();
        dto.setTotalGroups(allGroups.size());

        String targetGroupId = (groupId != null && !groupId.isEmpty()) ? groupId : (allGroups.isEmpty() ? null : allGroups.get(0).getId());

        List<Member> groupMembers = dataService.getMembersByGroupId(targetGroupId);
        long activeCount = groupMembers.stream().filter(m -> m.getStatus() == MemberStatus.ACTIVE).count();
        dto.setTotalActiveMembers((int) activeCount);

        LocalDate today = MonthlyBachatCalendar.today();
        List<CollectionRecord> currentMonthRecords = collectionService.getGroupCollectionsForPeriod(
                targetGroupId, today, YearMonth.from(today));

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
        List<CollectionRecord> openShares = currentMonthRecords.stream()
                .filter(record -> nz(record.getPendingAmount()).signum() > 0).toList();
        dto.setPendingShareCount(openShares.size());
        dto.setPendingShareTotal(openShares.stream().map(CollectionRecord::getPendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        if (expected.compareTo(BigDecimal.ZERO) > 0) {
            double pct = collected.doubleValue() / expected.doubleValue() * 100.0;
            dto.setCollectionPercentage(Math.round(pct * 10.0) / 10.0);
        }

        List<Transaction> ledger = dataService.getTransactionsByGroupId(targetGroupId);
        GroupFundService.FundBreakdown fund = groupFundService.getFundBreakdown(targetGroupId, ledger);
        dto.setTotalBachatCapital(fund.savings());
        dto.setTotalSavings(groupFundService.totalSavingsIncludingMemberAllocations(targetGroupId, fund));

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
        dto.setBankBalance(groupFundService.getBalanceByPaymentMethod(ledger, true));
        dto.setCashBalance(groupFundService.getBalanceByPaymentMethod(ledger, false));

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

        List<Loan> pendingLoans = groupLoans.stream().filter(loan -> loan.getStatus() == LoanStatus.ACTIVE
                && (nz(loan.getTotalOutstanding()).signum() != 0
                    ? nz(loan.getTotalOutstanding()) : nz(loan.getOutstandingPrincipal())).signum() > 0).toList();
        dto.setPendingLoanCount(pendingLoans.size());
        dto.setPendingLoanTotal(pendingLoans.stream().map(loan -> nz(loan.getTotalOutstanding()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        Group group = allGroups.stream().filter(candidate -> candidate.getId().equals(targetGroupId))
                .findFirst().orElse(null);
        dto.setUpcomingCollections(upcomingCollections(group, today, groupMembers, currentMonthRecords, groupLoans));
        dto.setRecentTransactions(ledger.stream().limit(10).toList());

        return dto;
    }

    private List<DashboardAdminDTO.UpcomingCollection> upcomingCollections(Group group, LocalDate today,
            List<Member> members, List<CollectionRecord> currentMonthRecords, List<Loan> loans) {
        if (members.isEmpty()) return List.of();
        BigDecimal monthlyBachat = group == null ? null : memberService.getEffectiveGroupMonthlyBachat(group);
        Map<String, List<CollectionRecord>> collectionsByMember = currentMonthRecords.stream()
                .filter(record -> record.getMemberId() != null)
                .collect(Collectors.groupingBy(CollectionRecord::getMemberId));
        Map<String, Loan> activeByMember = new LinkedHashMap<>();
        loans.stream().filter(loan -> loan.getStatus() == LoanStatus.ACTIVE)
                .forEach(loan -> activeByMember.putIfAbsent(loan.getMemberId(), loan));
        return members.stream().filter(member -> member.getStatus() == null || member.getStatus() == MemberStatus.ACTIVE)
                .limit(10).map(member -> {
                    Loan loan = activeByMember.get(member.getMemberId());
                    LocalDate dueDate = group == null ? member.getNextDueDate()
                            : collectionService.getNextBachatDueDate(group, member,
                                    collectionsByMember.getOrDefault(member.getMemberId(), List.of()), today);
                    return new DashboardAdminDTO.UpcomingCollection(member.getFullName(), member.getFullNameMr(),
                            member.getFullNameHi(), dueDate, group == null ? member.getMonthlyBachatAmount() : monthlyBachat,
                            loan != null ? loan.getMonthlyInstallment() : null,
                            loan != null ? loan.getOutstandingPrincipal() : null,
                            loan != null ? loan.getInterestRate() : null);
                }).toList();
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
        LocalDate today = MonthlyBachatCalendar.today();
        List<CollectionRecord> history = null;
        if (group != null) {
            dto.setRegistrationId(group.getRegistrationId());

            var currentMonth = java.time.YearMonth.from(today);
            boolean bachatStarted = MonthlyBachatCalendar.eligible(group, member, currentMonth);
            dto.setBachatStarted(bachatStarted);
            history = collectionService.getMemberHistory(member.getMemberId());
            dto.setNextBachatDueDate(collectionService.getNextBachatDueDate(group, member, history, today));
            GroupMasterData currentRules = dataService.getMasterDataForDate(group.getId(), today).orElse(null);
            BigDecimal monthlyBachat = currentRules != null && currentRules.getMonthlyBachatAmount() != null
                    ? currentRules.getMonthlyBachatAmount()
                    : (group.getMonthlyBachatAmount() != null ? group.getMonthlyBachatAmount() : BigDecimal.ZERO);
            dto.setMonthlyBachatAmount(monthlyBachat);
            dto.setPendingCollection(bachatStarted ? monthlyBachat : BigDecimal.ZERO);

            String currentBusinessKey = group.getId() + "_" + member.getMemberId() + "_"
                    + today.getMonthValue() + "_" + today.getYear();
            history.stream().filter(record -> bachatStarted && currentBusinessKey.equals(record.getBusinessKey()))
                    .findFirst().ifPresent(currentCollection -> {
                if (currentCollection.getExpectedAmount() != null) {
                    dto.setMonthlyBachatAmount(currentCollection.getExpectedAmount());
                }
                BigDecimal expected = currentCollection.getExpectedAmount() != null
                        ? currentCollection.getExpectedAmount() : BigDecimal.ZERO;
                BigDecimal paid = currentCollection.getPaidAmount() != null
                        ? currentCollection.getPaidAmount() : BigDecimal.ZERO;
                dto.setPendingCollection(expected.subtract(paid).max(BigDecimal.ZERO));
            });

            GroupFundService.FundBreakdown fund = groupFundService.getFundBreakdown(group.getId());
            BigDecimal availableCash = fund.availableFund();

            dto.setGroupRemainingFunds(availableCash);
            dto.setGroupLoansDisbursed(fund.loansDisbursed());
        }

        dto.setMyTotalSavings(member.overallSavingsBalance());
        dto.setMyBachatSavings(member.getStatus() == MemberStatus.EXITED ? dto.getMyTotalSavings()
                : dto.getMyTotalSavings().subtract(member.getMyIncome()));
        dto.setMyCurrentShare(member.getCurrentShareBalance() != null ? member.getCurrentShareBalance() : BigDecimal.ZERO);
        dto.setMyIncome(member.getMyIncome());
        dto.setMyPenalties(collectionService.getCurrentPenalty(member, today, history));
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
                dto.setRemainingTenure(remainingSchedule.size());
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
