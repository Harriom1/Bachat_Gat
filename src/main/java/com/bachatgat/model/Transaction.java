package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Transaction {
    @DocumentId
    private String id;
    private String transactionId; // e.g. TXN-2026-0001
    private String groupId;
    private String memberId;
    private String memberName;
    private String loanId;
    private TransactionType type;
    private BigDecimal amount = BigDecimal.ZERO;
    private BigDecimal principalAmount = BigDecimal.ZERO;
    private BigDecimal interestAmount = BigDecimal.ZERO;
    private LocalDate date = LocalDate.now();
    private String reference;
    private String description;
    private String paymentMethod = "CASH";
    private String paymentStatus = "SUCCESS"; // INITIATED, PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED
    private String category; // SHARE, LOAN, OTHER, EXPENSE, DISTRIBUTION
    private String idempotencyKey;
    private String createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Transaction() {}

    public Transaction(String transactionId, String groupId, String memberId, String memberName, String loanId, 
                       TransactionType type, BigDecimal amount, BigDecimal principalAmount, BigDecimal interestAmount, 
                       LocalDate date, String reference, String description, String paymentMethod, String createdBy) {
        this.transactionId = transactionId;
        this.id = transactionId;
        this.groupId = groupId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.loanId = loanId;
        this.type = type;
        this.amount = amount;
        this.principalAmount = principalAmount != null ? principalAmount : BigDecimal.ZERO;
        this.interestAmount = interestAmount != null ? interestAmount : BigDecimal.ZERO;
        this.date = date != null ? date : LocalDate.now();
        this.reference = reference;
        this.description = description;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = "SUCCESS";
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public String getLoanId() { return loanId; }
    public void setLoanId(String loanId) { this.loanId = loanId; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }
    public BigDecimal getInterestAmount() { return interestAmount; }
    public void setInterestAmount(BigDecimal interestAmount) { this.interestAmount = interestAmount; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

