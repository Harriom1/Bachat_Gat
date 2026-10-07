package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class LoanRepaymentSchedule {
    @DocumentId
    private String id;
    private String loanId;
    private String groupId;
    private String memberId;
    private int installmentNo;
    private LocalDate dueDate;
    private BigDecimal openingPrincipal = BigDecimal.ZERO;
    private BigDecimal emiAmount = BigDecimal.ZERO;
    private BigDecimal principalAmount = BigDecimal.ZERO;
    private BigDecimal interestAmount = BigDecimal.ZERO;
    private BigDecimal penaltyAmount = BigDecimal.ZERO;
    private BigDecimal totalDue = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private BigDecimal outstandingAmount = BigDecimal.ZERO;
    private BigDecimal closingPrincipal = BigDecimal.ZERO;
    private RepaymentStatus status = RepaymentStatus.UPCOMING;
    private LocalDate paidDate;
    private String transactionId;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public LoanRepaymentSchedule() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLoanId() { return loanId; }
    public void setLoanId(String loanId) { this.loanId = loanId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public int getInstallmentNo() { return installmentNo; }
    public int getInstallmentNumber() { return installmentNo; }
    public void setInstallmentNo(int installmentNo) { this.installmentNo = installmentNo; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getOpeningPrincipal() { return openingPrincipal != null ? openingPrincipal : BigDecimal.ZERO; }
    public void setOpeningPrincipal(BigDecimal openingPrincipal) { this.openingPrincipal = openingPrincipal; }
    public BigDecimal getEmiAmount() { return emiAmount != null ? emiAmount : totalDue; }
    public void setEmiAmount(BigDecimal emiAmount) { this.emiAmount = emiAmount; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public BigDecimal getPrincipalComponent() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }
    public BigDecimal getInterestAmount() { return interestAmount; }
    public BigDecimal getInterestComponent() { return interestAmount; }
    public void setInterestAmount(BigDecimal interestAmount) { this.interestAmount = interestAmount; }
    public BigDecimal getPenaltyAmount() { return penaltyAmount != null ? penaltyAmount : BigDecimal.ZERO; }
    public void setPenaltyAmount(BigDecimal penaltyAmount) { this.penaltyAmount = penaltyAmount; }
    public BigDecimal getTotalDue() { return totalDue; }
    public void setTotalDue(BigDecimal totalDue) { this.totalDue = totalDue; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getOutstandingAmount() { return outstandingAmount; }
    public void setOutstandingAmount(BigDecimal outstandingAmount) { this.outstandingAmount = outstandingAmount; }
    public BigDecimal getClosingPrincipal() { return closingPrincipal != null ? closingPrincipal : BigDecimal.ZERO; }
    public void setClosingPrincipal(BigDecimal closingPrincipal) { this.closingPrincipal = closingPrincipal; }
    public BigDecimal getRemainingPrincipal() { return getClosingPrincipal(); }
    public void setRemainingPrincipal(BigDecimal remainingPrincipal) { this.closingPrincipal = remainingPrincipal; }
    public RepaymentStatus getStatus() { return status; }
    public void setStatus(RepaymentStatus status) { this.status = status; }
    public LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
