package com.bachatgat.dto;

import com.bachatgat.model.Loan;
import com.bachatgat.model.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DashboardUserDTO {
    private String memberName;
    private String memberId;
    private String groupName;
    private String registrationId;
    private String membershipStatus;

    // Self savings & shares
    private BigDecimal myTotalSavings = BigDecimal.ZERO;
    private BigDecimal myCurrentShare = BigDecimal.ZERO;
    private BigDecimal myIncome = BigDecimal.ZERO;           // Allocated loan interest + other group income
    private BigDecimal myPenalties = BigDecimal.ZERO;        // Pending penalties/late fees
    private BigDecimal netFinancialPosition = BigDecimal.ZERO; // Savings + Income - Loan - Penalties
    private BigDecimal pendingCollection = BigDecimal.ZERO;
    private BigDecimal monthlyBachatAmount = BigDecimal.ZERO;

    // Self loan details
    private String activeLoanId;
    private BigDecimal myLoanAmount = BigDecimal.ZERO;
    private BigDecimal myLoanOutstanding = BigDecimal.ZERO;
    private BigDecimal myInterestRate = BigDecimal.ZERO;
    private BigDecimal totalInterestPaid = BigDecimal.ZERO;
    private BigDecimal nextPaymentAmount = BigDecimal.ZERO;
    private LocalDate nextPaymentDueDate;
    private BigDecimal currentEmi = BigDecimal.ZERO;
    private BigDecimal currentPrincipal = BigDecimal.ZERO;
    private BigDecimal currentInterest = BigDecimal.ZERO;
    private BigDecimal nextEmi = BigDecimal.ZERO;
    private BigDecimal nextPrincipal = BigDecimal.ZERO;
    private BigDecimal nextInterest = BigDecimal.ZERO;
    private int remainingTenure;
    
    private Loan activeLoan;
    private List<Transaction> recentTransactions = new ArrayList<>();

    // Group financial transparency
    private BigDecimal groupRemainingFunds = BigDecimal.ZERO;
    private BigDecimal groupLoansDisbursed = BigDecimal.ZERO;

    public DashboardUserDTO() {}

    // Getters and Setters
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getRegistrationId() { return registrationId; }
    public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }
    public String getMembershipStatus() { return membershipStatus; }
    public void setMembershipStatus(String membershipStatus) { this.membershipStatus = membershipStatus; }
    public BigDecimal getMyTotalSavings() { return myTotalSavings; }
    public void setMyTotalSavings(BigDecimal myTotalSavings) { this.myTotalSavings = myTotalSavings; }
    public BigDecimal getMyCurrentShare() { return myCurrentShare; }
    public void setMyCurrentShare(BigDecimal myCurrentShare) { this.myCurrentShare = myCurrentShare; }
    public BigDecimal getMyIncome() { return myIncome; }
    public void setMyIncome(BigDecimal myIncome) { this.myIncome = myIncome; }
    public BigDecimal getMyPenalties() { return myPenalties; }
    public void setMyPenalties(BigDecimal myPenalties) { this.myPenalties = myPenalties; }
    public BigDecimal getNetFinancialPosition() { return netFinancialPosition; }
    public void setNetFinancialPosition(BigDecimal netFinancialPosition) { this.netFinancialPosition = netFinancialPosition; }
    public BigDecimal getPendingCollection() { return pendingCollection; }
    public void setPendingCollection(BigDecimal pendingCollection) { this.pendingCollection = pendingCollection; }
    public BigDecimal getMonthlyBachatAmount() { return monthlyBachatAmount; }
    public void setMonthlyBachatAmount(BigDecimal monthlyBachatAmount) { this.monthlyBachatAmount = monthlyBachatAmount; }
    public String getActiveLoanId() { return activeLoanId; }
    public void setActiveLoanId(String activeLoanId) { this.activeLoanId = activeLoanId; }
    public BigDecimal getMyLoanAmount() { return myLoanAmount; }
    public void setMyLoanAmount(BigDecimal myLoanAmount) { this.myLoanAmount = myLoanAmount; }
    public BigDecimal getMyLoanOutstanding() { return myLoanOutstanding; }
    public void setMyLoanOutstanding(BigDecimal myLoanOutstanding) { this.myLoanOutstanding = myLoanOutstanding; }
    public BigDecimal getMyInterestRate() { return myInterestRate; }
    public void setMyInterestRate(BigDecimal myInterestRate) { this.myInterestRate = myInterestRate; }
    public BigDecimal getTotalInterestPaid() { return totalInterestPaid; }
    public void setTotalInterestPaid(BigDecimal totalInterestPaid) { this.totalInterestPaid = totalInterestPaid; }
    public BigDecimal getNextPaymentAmount() { return nextPaymentAmount; }
    public void setNextPaymentAmount(BigDecimal nextPaymentAmount) { this.nextPaymentAmount = nextPaymentAmount; }
    public LocalDate getNextPaymentDueDate() { return nextPaymentDueDate; }
    public void setNextPaymentDueDate(LocalDate nextPaymentDueDate) { this.nextPaymentDueDate = nextPaymentDueDate; }
    public BigDecimal getCurrentEmi() { return currentEmi; }
    public void setCurrentEmi(BigDecimal currentEmi) { this.currentEmi = currentEmi; }
    public BigDecimal getCurrentPrincipal() { return currentPrincipal; }
    public void setCurrentPrincipal(BigDecimal currentPrincipal) { this.currentPrincipal = currentPrincipal; }
    public BigDecimal getCurrentInterest() { return currentInterest; }
    public void setCurrentInterest(BigDecimal currentInterest) { this.currentInterest = currentInterest; }
    public BigDecimal getNextEmi() { return nextEmi; }
    public void setNextEmi(BigDecimal nextEmi) { this.nextEmi = nextEmi; }
    public BigDecimal getNextPrincipal() { return nextPrincipal; }
    public void setNextPrincipal(BigDecimal nextPrincipal) { this.nextPrincipal = nextPrincipal; }
    public BigDecimal getNextInterest() { return nextInterest; }
    public void setNextInterest(BigDecimal nextInterest) { this.nextInterest = nextInterest; }
    public int getRemainingTenure() { return remainingTenure; }
    public void setRemainingTenure(int remainingTenure) { this.remainingTenure = remainingTenure; }
    public Loan getActiveLoan() { return activeLoan; }
    public void setActiveLoan(Loan activeLoan) { this.activeLoan = activeLoan; }
    public List<Transaction> getRecentTransactions() { return recentTransactions; }
    public void setRecentTransactions(List<Transaction> recentTransactions) { this.recentTransactions = recentTransactions; }

    public BigDecimal getGroupRemainingFunds() { return groupRemainingFunds != null ? groupRemainingFunds : BigDecimal.ZERO; }
    public void setGroupRemainingFunds(BigDecimal groupRemainingFunds) { this.groupRemainingFunds = groupRemainingFunds; }
    public BigDecimal getGroupLoansDisbursed() { return groupLoansDisbursed != null ? groupLoansDisbursed : BigDecimal.ZERO; }
    public void setGroupLoansDisbursed(BigDecimal groupLoansDisbursed) { this.groupLoansDisbursed = groupLoansDisbursed; }
}
