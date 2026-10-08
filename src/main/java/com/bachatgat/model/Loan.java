package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Loan {
    @DocumentId
    private String id;
    private String loanId;               // e.g. LN-2026-001
    private String applicationId;
    private String groupId;
    private String groupName;
    private String memberId;
    private String memberName;

    // Financial calculations
    private BigDecimal principalAmount = BigDecimal.ZERO;
    private BigDecimal interestRate = BigDecimal.valueOf(12.0); // Annual %
    private int durationMonths = 12;
    private String interestType = "FLAT"; // FLAT or REDUCING
    
    private BigDecimal totalInterest = BigDecimal.ZERO;
    private BigDecimal totalPayable = BigDecimal.ZERO;
    
    private BigDecimal principalPaid = BigDecimal.ZERO;
    private BigDecimal interestPaid = BigDecimal.ZERO;
    private BigDecimal totalPaid = BigDecimal.ZERO;
    
    private BigDecimal outstandingPrincipal = BigDecimal.ZERO;
    private BigDecimal outstandingInterest = BigDecimal.ZERO;
    private BigDecimal totalOutstanding = BigDecimal.ZERO;
    
    private BigDecimal monthlyInstallment = BigDecimal.ZERO;
    private LocalDate disbursementDate = LocalDate.now();
    private LocalDate nextDueDate;
    private int overdueInstallments = 0;
    
    private String purpose = "General / Household Need";
    private String chequeNumber;
    private LoanStatus status = LoanStatus.ACTIVE;
    private String createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Loan() {}

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getChequeNumber() { return chequeNumber; }
    public void setChequeNumber(String chequeNumber) { this.chequeNumber = chequeNumber; }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLoanId() { return loanId; }
    public void setLoanId(String loanId) { this.loanId = loanId; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public int getDurationMonths() { return durationMonths; }
    public void setDurationMonths(int durationMonths) { this.durationMonths = durationMonths; }
    public String getInterestType() { return interestType; }
    public void setInterestType(String interestType) { this.interestType = interestType; }
    public BigDecimal getTotalInterest() { return totalInterest; }
    public void setTotalInterest(BigDecimal totalInterest) { this.totalInterest = totalInterest; }
    public BigDecimal getTotalPayable() { return totalPayable; }
    public void setTotalPayable(BigDecimal totalPayable) { this.totalPayable = totalPayable; }
    public BigDecimal getPrincipalPaid() { return principalPaid; }
    public void setPrincipalPaid(BigDecimal principalPaid) { this.principalPaid = principalPaid; }
    public BigDecimal getInterestPaid() { return interestPaid; }
    public void setInterestPaid(BigDecimal interestPaid) { this.interestPaid = interestPaid; }
    public BigDecimal getTotalPaid() { return totalPaid; }
    public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }
    public BigDecimal getOutstandingPrincipal() { return outstandingPrincipal; }
    public void setOutstandingPrincipal(BigDecimal outstandingPrincipal) { this.outstandingPrincipal = outstandingPrincipal; }
    public BigDecimal getOutstandingInterest() { return outstandingInterest; }
    public void setOutstandingInterest(BigDecimal outstandingInterest) { this.outstandingInterest = outstandingInterest; }
    public BigDecimal getTotalOutstanding() { return totalOutstanding; }
    public void setTotalOutstanding(BigDecimal totalOutstanding) { this.totalOutstanding = totalOutstanding; }
    public BigDecimal getMonthlyInstallment() { return monthlyInstallment; }
    public void setMonthlyInstallment(BigDecimal monthlyInstallment) { this.monthlyInstallment = monthlyInstallment; }
    public LocalDate getDisbursementDate() { return disbursementDate; }
    public void setDisbursementDate(LocalDate disbursementDate) { this.disbursementDate = disbursementDate; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }
    public LoanStatus getStatus() { return status; }
    public void setStatus(LoanStatus status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public int getOverdueInstallments() { return overdueInstallments; }
    public void setOverdueInstallments(int overdueInstallments) { this.overdueInstallments = overdueInstallments; }
}
