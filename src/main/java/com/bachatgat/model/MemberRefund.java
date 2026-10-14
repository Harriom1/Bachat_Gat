package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Backend-owned exit refund record. The reservation is represented by one REFUND ledger entry. */
public class MemberRefund {
    @DocumentId
    private String id;
    private String refundId;
    private String groupId;
    private String memberId;
    private String memberName;
    private BigDecimal amount = BigDecimal.ZERO;
    private String maskedBankAccount;
    private LocalDate requestDate = LocalDate.now();
    private LocalDate expectedCreditDate;
    private String status = "PENDING"; // PENDING, PROCESSING, COMPLETED, FAILED
    private LocalDateTime processedAt;
    private LocalDateTime completedAt;
    private String failureReason;
    private String createdBy;
    private String processedBy;
    private boolean balanceReserved;
    private String reservationTransactionId;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public MemberRefund() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRefundId() { return refundId; }
    public void setRefundId(String refundId) { this.refundId = refundId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getMaskedBankAccount() { return maskedBankAccount; }
    public void setMaskedBankAccount(String maskedBankAccount) { this.maskedBankAccount = maskedBankAccount; }
    public LocalDate getRequestDate() { return requestDate; }
    public void setRequestDate(LocalDate requestDate) { this.requestDate = requestDate; }
    public LocalDate getExpectedCreditDate() { return expectedCreditDate; }
    public void setExpectedCreditDate(LocalDate expectedCreditDate) { this.expectedCreditDate = expectedCreditDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getProcessedBy() { return processedBy; }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }
    public boolean isBalanceReserved() { return balanceReserved; }
    public void setBalanceReserved(boolean balanceReserved) { this.balanceReserved = balanceReserved; }
    public String getReservationTransactionId() { return reservationTransactionId; }
    public void setReservationTransactionId(String reservationTransactionId) { this.reservationTransactionId = reservationTransactionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
