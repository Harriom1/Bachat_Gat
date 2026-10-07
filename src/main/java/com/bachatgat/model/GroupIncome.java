package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class GroupIncome {
    @DocumentId
    private String id;
    private String groupId;
    private String source; // INTEREST_INCOME, DONATION, GRANT, MISC, BANK_INTEREST, OTHER
    private String category; // LOAN_INTEREST, OTHER, etc.
    private String description;
    private BigDecimal amount = BigDecimal.ZERO;
    private LocalDate incomeDate = LocalDate.now();
    private String paymentMethod = "CASH";
    private String reference;
    private String distributionRule = "GROUP_LEVEL_ONLY"; // EQUAL_ACTIVE_MEMBERS, PROPORTIONAL_SAVINGS, SELECTED_MEMBERS, MANUAL, GROUP_LEVEL_ONLY
    private boolean distributed = false;
    private BigDecimal distributedAmount = BigDecimal.ZERO;
    private String recordedBy;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public GroupIncome() {}

    public GroupIncome(String groupId, String source, String description, BigDecimal amount, LocalDate incomeDate, String paymentMethod, String reference, String distributionRule, String recordedBy) {
        this.id = "INC-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        this.groupId = groupId;
        this.source = source;
        this.description = description;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.incomeDate = incomeDate != null ? incomeDate : LocalDate.now();
        this.paymentMethod = paymentMethod != null ? paymentMethod : "CASH";
        this.reference = reference;
        this.distributionRule = distributionRule != null ? distributionRule : "GROUP_LEVEL_ONLY";
        this.recordedBy = recordedBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getIncomeDate() { return incomeDate; }
    public void setIncomeDate(LocalDate incomeDate) { this.incomeDate = incomeDate; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getDistributionRule() { return distributionRule; }
    public void setDistributionRule(String distributionRule) { this.distributionRule = distributionRule; }
    public String getCategory() { return category != null ? category : source; }
    public void setCategory(String category) { this.category = category; }
    public boolean isDistributed() { return distributed; }
    public void setDistributed(boolean distributed) { this.distributed = distributed; }
    public BigDecimal getDistributedAmount() { return distributedAmount; }
    public void setDistributedAmount(BigDecimal distributedAmount) { this.distributedAmount = distributedAmount; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
