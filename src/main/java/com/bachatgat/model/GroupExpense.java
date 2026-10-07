package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class GroupExpense {
    @DocumentId
    private String id;
    private String groupId;
    private String category; // STATIONERY, AUDIT_FEE, TEA_SNACKS, TRAVEL, BANK_CHARGES, OTHER
    private String description;
    private BigDecimal amount = BigDecimal.ZERO;
    private LocalDate expenseDate = LocalDate.now();
    private String paymentMode = "CASH";
    private String paidTo;
    private String recordedBy;
    private String reference;
    private String receiptPath;
    private String distributionRule = "GROUP_LEVEL_ONLY"; // EQUAL_ACTIVE_MEMBERS, PROPORTIONAL_SAVINGS, SELECTED_MEMBERS, MANUAL, GROUP_LEVEL_ONLY
    private boolean distributed = false;
    private BigDecimal distributedAmount = BigDecimal.ZERO;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();


    public GroupExpense() {}

    public GroupExpense(String groupId, String category, String description, BigDecimal amount, LocalDate expenseDate, String paymentMode, String paidTo, String recordedBy) {
        this.id = "EXP-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        this.groupId = groupId;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.expenseDate = expenseDate != null ? expenseDate : LocalDate.now();
        this.paymentMode = paymentMode;
        this.paidTo = paidTo;
        this.recordedBy = recordedBy;
        this.distributionRule = "GROUP_LEVEL_ONLY";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public String getPaidTo() { return paidTo; }
    public void setPaidTo(String paidTo) { this.paidTo = paidTo; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public String getReceiptPath() { return receiptPath; }
    public void setReceiptPath(String receiptPath) { this.receiptPath = receiptPath; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getDistributionRule() { return distributionRule; }
    public void setDistributionRule(String distributionRule) { this.distributionRule = distributionRule; }
    public boolean isDistributed() { return distributed; }
    public void setDistributed(boolean distributed) { this.distributed = distributed; }
    public BigDecimal getDistributedAmount() { return distributedAmount; }
    public void setDistributedAmount(BigDecimal distributedAmount) { this.distributedAmount = distributedAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

