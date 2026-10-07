package com.bachatgat.dto;

import com.bachatgat.model.Transaction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DashboardAdminDTO {
    private int totalGroups = 0;
    private int totalActiveMembers = 0;
    private BigDecimal monthlyExpectedCollection = BigDecimal.ZERO;
    private BigDecimal monthlyCollected = BigDecimal.ZERO;
    private BigDecimal monthlyPending = BigDecimal.ZERO;
    private double collectionPercentage = 0.0;
    
    private BigDecimal totalSavings = BigDecimal.ZERO;
    private BigDecimal totalLoansDisbursed = BigDecimal.ZERO;
    private BigDecimal totalPrincipalRecovered = BigDecimal.ZERO;
    private BigDecimal totalOutstandingLoans = BigDecimal.ZERO;
    private BigDecimal totalInterestCollected = BigDecimal.ZERO;
    private BigDecimal totalOtherIncome = BigDecimal.ZERO;
    private BigDecimal totalExpenses = BigDecimal.ZERO;
    private BigDecimal groupAvailableFund = BigDecimal.ZERO;
    private BigDecimal totalGroupCorpus = BigDecimal.ZERO;
    private BigDecimal cashBalance = BigDecimal.ZERO;
    private BigDecimal bankBalance = BigDecimal.ZERO;

    private int pendingLoanApplicationsCount = 0;
    private int approvedLoansCount = 0;
    private int activeLoansCount = 0;
    private int overdueLoansCount = 0;

    private List<Transaction> recentTransactions = new ArrayList<>();

    public DashboardAdminDTO() {}

    // Getters and Setters
    public int getTotalGroups() { return totalGroups; }
    public void setTotalGroups(int totalGroups) { this.totalGroups = totalGroups; }
    public int getTotalActiveMembers() { return totalActiveMembers; }
    public void setTotalActiveMembers(int totalActiveMembers) { this.totalActiveMembers = totalActiveMembers; }
    public BigDecimal getMonthlyExpectedCollection() { return monthlyExpectedCollection; }
    public void setMonthlyExpectedCollection(BigDecimal monthlyExpectedCollection) { this.monthlyExpectedCollection = monthlyExpectedCollection; }
    public BigDecimal getMonthlyCollected() { return monthlyCollected; }
    public void setMonthlyCollected(BigDecimal monthlyCollected) { this.monthlyCollected = monthlyCollected; }
    public BigDecimal getMonthlyPending() { return monthlyPending; }
    public void setMonthlyPending(BigDecimal monthlyPending) { this.monthlyPending = monthlyPending; }
    public double getCollectionPercentage() { return collectionPercentage; }
    public void setCollectionPercentage(double collectionPercentage) { this.collectionPercentage = collectionPercentage; }
    public BigDecimal getTotalSavings() { return totalSavings; }
    public void setTotalSavings(BigDecimal totalSavings) { this.totalSavings = totalSavings; }
    public BigDecimal getTotalLoansDisbursed() { return totalLoansDisbursed; }
    public void setTotalLoansDisbursed(BigDecimal totalLoansDisbursed) { this.totalLoansDisbursed = totalLoansDisbursed; }
    public BigDecimal getTotalPrincipalRecovered() { return totalPrincipalRecovered; }
    public void setTotalPrincipalRecovered(BigDecimal totalPrincipalRecovered) { this.totalPrincipalRecovered = totalPrincipalRecovered; }
    public BigDecimal getTotalOutstandingLoans() { return totalOutstandingLoans; }
    public void setTotalOutstandingLoans(BigDecimal totalOutstandingLoans) { this.totalOutstandingLoans = totalOutstandingLoans; }
    public BigDecimal getTotalInterestCollected() { return totalInterestCollected; }
    public void setTotalInterestCollected(BigDecimal totalInterestCollected) { this.totalInterestCollected = totalInterestCollected; }
    public BigDecimal getTotalOtherIncome() { return totalOtherIncome; }
    public void setTotalOtherIncome(BigDecimal totalOtherIncome) { this.totalOtherIncome = totalOtherIncome; }
    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }
    public BigDecimal getGroupAvailableFund() { return groupAvailableFund; }
    public void setGroupAvailableFund(BigDecimal groupAvailableFund) { this.groupAvailableFund = groupAvailableFund; }
    public BigDecimal getTotalGroupCorpus() { return totalGroupCorpus; }
    public void setTotalGroupCorpus(BigDecimal totalGroupCorpus) { this.totalGroupCorpus = totalGroupCorpus; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }
    public BigDecimal getBankBalance() { return bankBalance; }
    public void setBankBalance(BigDecimal bankBalance) { this.bankBalance = bankBalance; }
    public int getPendingLoanApplicationsCount() { return pendingLoanApplicationsCount; }
    public void setPendingLoanApplicationsCount(int pendingLoanApplicationsCount) { this.pendingLoanApplicationsCount = pendingLoanApplicationsCount; }
    public int getApprovedLoansCount() { return approvedLoansCount; }
    public void setApprovedLoansCount(int approvedLoansCount) { this.approvedLoansCount = approvedLoansCount; }
    public int getActiveLoansCount() { return activeLoansCount; }
    public void setActiveLoansCount(int activeLoansCount) { this.activeLoansCount = activeLoansCount; }
    public int getOverdueLoansCount() { return overdueLoansCount; }
    public void setOverdueLoansCount(int overdueLoansCount) { this.overdueLoansCount = overdueLoansCount; }
    public List<Transaction> getRecentTransactions() { return recentTransactions; }
    public void setRecentTransactions(List<Transaction> recentTransactions) { this.recentTransactions = recentTransactions; }
}
