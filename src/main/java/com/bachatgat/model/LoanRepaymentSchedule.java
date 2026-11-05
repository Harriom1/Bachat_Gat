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
    /** Principal scheduled for this original installment, excluding carried arrears. */
    private BigDecimal scheduledPrincipalAmount = BigDecimal.ZERO;
    private BigDecimal interestAmount = BigDecimal.ZERO;
    private BigDecimal penaltyAmount = BigDecimal.ZERO;
    private BigDecimal totalDue = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private BigDecimal paidPrincipalAmount = BigDecimal.ZERO;
    private BigDecimal paidInterestAmount = BigDecimal.ZERO;
    private BigDecimal outstandingAmount = BigDecimal.ZERO;
    private BigDecimal pendingInterest = BigDecimal.ZERO;
    private BigDecimal pendingPrincipal = BigDecimal.ZERO;
    /** Annual rate used to calculate this installment. */
    private BigDecimal annualInterestRate = BigDecimal.ZERO;
    /** Monthly rate used to calculate this installment. */
    private BigDecimal monthlyInterestRate = BigDecimal.ZERO;
    /** Contractual EMI before carried arrears are added. */
    private BigDecimal originalScheduledEmi = BigDecimal.ZERO;
    /** Carried unpaid interest from earlier installments. */
    private BigDecimal previousPendingInterest = BigDecimal.ZERO;
    /** Carried unpaid principal from earlier installments. */
    private BigDecimal previousPendingPrincipal = BigDecimal.ZERO;
    /** Complete interest due for this row, including carried interest. */
    private BigDecimal totalInterestDue = BigDecimal.ZERO;
    /** Complete principal due for this row, including carried principal. */
    private BigDecimal totalPrincipalDue = BigDecimal.ZERO;
    /** Remaining unpaid interest after this row's payment. */
    private BigDecimal remainingPendingInterest = BigDecimal.ZERO;
    /** Remaining unpaid principal after this row's payment. */
    private BigDecimal remainingPendingPrincipal = BigDecimal.ZERO;
    private String arrangementType = "NORMAL";
    private String exceptionApprovedBy;
    private LocalDateTime exceptionApprovedAt;
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
    public BigDecimal getScheduledPrincipalAmount() {
        return scheduledPrincipalAmount != null && scheduledPrincipalAmount.signum() != 0
                ? scheduledPrincipalAmount : principalAmount;
    }
    public void setScheduledPrincipalAmount(BigDecimal scheduledPrincipalAmount) {
        this.scheduledPrincipalAmount = scheduledPrincipalAmount;
    }
    public BigDecimal getInterestAmount() { return interestAmount; }
    public BigDecimal getInterestComponent() { return interestAmount; }
    public void setInterestAmount(BigDecimal interestAmount) { this.interestAmount = interestAmount; }
    public BigDecimal getPenaltyAmount() { return penaltyAmount != null ? penaltyAmount : BigDecimal.ZERO; }
    public void setPenaltyAmount(BigDecimal penaltyAmount) { this.penaltyAmount = penaltyAmount; }
    public BigDecimal getTotalDue() { return totalDue; }
    public void setTotalDue(BigDecimal totalDue) { this.totalDue = totalDue; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getPaidPrincipalAmount() { return paidPrincipalAmount != null ? paidPrincipalAmount : BigDecimal.ZERO; }
    public void setPaidPrincipalAmount(BigDecimal paidPrincipalAmount) { this.paidPrincipalAmount = paidPrincipalAmount; }
    public BigDecimal getPaidInterestAmount() { return paidInterestAmount != null ? paidInterestAmount : BigDecimal.ZERO; }
    public void setPaidInterestAmount(BigDecimal paidInterestAmount) { this.paidInterestAmount = paidInterestAmount; }
    public BigDecimal getOutstandingAmount() { return outstandingAmount; }
    public void setOutstandingAmount(BigDecimal outstandingAmount) { this.outstandingAmount = outstandingAmount; }
    public BigDecimal getPendingInterest() { return pendingInterest != null ? pendingInterest : BigDecimal.ZERO; }
    public void setPendingInterest(BigDecimal pendingInterest) { this.pendingInterest = pendingInterest; }
    public BigDecimal getPendingPrincipal() { return pendingPrincipal != null ? pendingPrincipal : BigDecimal.ZERO; }
    public void setPendingPrincipal(BigDecimal pendingPrincipal) { this.pendingPrincipal = pendingPrincipal; }
    public BigDecimal getAnnualInterestRate() { return annualInterestRate != null ? annualInterestRate : BigDecimal.ZERO; }
    public void setAnnualInterestRate(BigDecimal annualInterestRate) { this.annualInterestRate = annualInterestRate; }
    public BigDecimal getMonthlyInterestRate() { return monthlyInterestRate != null ? monthlyInterestRate : BigDecimal.ZERO; }
    public void setMonthlyInterestRate(BigDecimal monthlyInterestRate) { this.monthlyInterestRate = monthlyInterestRate; }
    public BigDecimal getOriginalScheduledEmi() { return originalScheduledEmi != null ? originalScheduledEmi : getEmiAmount(); }
    public void setOriginalScheduledEmi(BigDecimal originalScheduledEmi) { this.originalScheduledEmi = originalScheduledEmi; }
    public BigDecimal getPreviousPendingInterest() { return previousPendingInterest != null ? previousPendingInterest : BigDecimal.ZERO; }
    public void setPreviousPendingInterest(BigDecimal previousPendingInterest) { this.previousPendingInterest = previousPendingInterest; }
    public BigDecimal getPreviousPendingPrincipal() { return previousPendingPrincipal != null ? previousPendingPrincipal : BigDecimal.ZERO; }
    public void setPreviousPendingPrincipal(BigDecimal previousPendingPrincipal) { this.previousPendingPrincipal = previousPendingPrincipal; }
    public BigDecimal getTotalInterestDue() { return totalInterestDue != null ? totalInterestDue : getInterestAmount(); }
    public void setTotalInterestDue(BigDecimal totalInterestDue) { this.totalInterestDue = totalInterestDue; }
    public BigDecimal getTotalPrincipalDue() { return totalPrincipalDue != null ? totalPrincipalDue : getPrincipalAmount(); }
    public void setTotalPrincipalDue(BigDecimal totalPrincipalDue) { this.totalPrincipalDue = totalPrincipalDue; }
    public BigDecimal getRemainingPendingInterest() { return remainingPendingInterest != null ? remainingPendingInterest : getPendingInterest(); }
    public void setRemainingPendingInterest(BigDecimal remainingPendingInterest) { this.remainingPendingInterest = remainingPendingInterest; }
    public BigDecimal getRemainingPendingPrincipal() { return remainingPendingPrincipal != null ? remainingPendingPrincipal : getPendingPrincipal(); }
    public void setRemainingPendingPrincipal(BigDecimal remainingPendingPrincipal) { this.remainingPendingPrincipal = remainingPendingPrincipal; }
    public String getPaymentStatus() { return status == null ? null : status.name(); }
    public String getArrangementType() { return arrangementType == null ? "NORMAL" : arrangementType; }
    public void setArrangementType(String arrangementType) { this.arrangementType = arrangementType; }
    public String getExceptionApprovedBy() { return exceptionApprovedBy; }
    public void setExceptionApprovedBy(String exceptionApprovedBy) { this.exceptionApprovedBy = exceptionApprovedBy; }
    public LocalDateTime getExceptionApprovedAt() { return exceptionApprovedAt; }
    public void setExceptionApprovedAt(LocalDateTime exceptionApprovedAt) { this.exceptionApprovedAt = exceptionApprovedAt; }
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
