package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class MemberAdjustment {
    @DocumentId
    private String id;
    private String groupId;
    private String memberId;
    private String memberName;
    private String sourceType; // OTHER_INCOME, OTHER_EXPENSE, MANUAL_ADJUSTMENT
    private String sourceId;   // Income ID or Expense ID
    private String description;
    private BigDecimal amount = BigDecimal.ZERO; // Positive for income credit, negative or debit for expense share
    private BigDecimal previousBalance = BigDecimal.ZERO;
    private BigDecimal newBalance = BigDecimal.ZERO;
    private LocalDate adjustmentDate = LocalDate.now();
    private String recordedBy;
    private LocalDateTime createdAt = LocalDateTime.now();

    public MemberAdjustment() {}

    public MemberAdjustment(String groupId, String memberId, String memberName, String sourceType, String sourceId,
                            String description, BigDecimal amount, BigDecimal previousBalance, BigDecimal newBalance,
                            LocalDate adjustmentDate, String recordedBy) {
        this.id = "ADJ-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        this.groupId = groupId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.description = description;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.previousBalance = previousBalance != null ? previousBalance : BigDecimal.ZERO;
        this.newBalance = newBalance != null ? newBalance : BigDecimal.ZERO;
        this.adjustmentDate = adjustmentDate != null ? adjustmentDate : LocalDate.now();
        this.recordedBy = recordedBy;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getPreviousBalance() { return previousBalance; }
    public void setPreviousBalance(BigDecimal previousBalance) { this.previousBalance = previousBalance; }
    public BigDecimal getNewBalance() { return newBalance; }
    public void setNewBalance(BigDecimal newBalance) { this.newBalance = newBalance; }
    public LocalDate getAdjustmentDate() { return adjustmentDate; }
    public void setAdjustmentDate(LocalDate adjustmentDate) { this.adjustmentDate = adjustmentDate; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
