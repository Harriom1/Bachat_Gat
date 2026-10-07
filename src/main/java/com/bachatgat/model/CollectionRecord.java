package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CollectionRecord {
    @DocumentId
    private String id;
    private String businessKey; // groupId + "_" + memberId + "_" + month + "_" + year
    private String groupId;
    private String memberId;
    private String memberName;
    private int month;          // 1 - 12
    private int year;           // e.g. 2026
    private BigDecimal expectedAmount;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private BigDecimal pendingAmount;
    private LocalDate dueDate;
    private int daysLate = 0;
    private BigDecimal lateFeeAmount = BigDecimal.ZERO;
    private BigDecimal waivedLateFee = BigDecimal.ZERO;
    private BigDecimal paidLateFee = BigDecimal.ZERO;
    private LocalDate paymentDate;
    private String paymentMethod = "CASH"; // CASH, UPI, BANK_TRANSFER
    private String referenceNumber;
    private CollectionStatus status = CollectionStatus.PENDING;
    private String recordedBy;
    private String notes;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();


    public CollectionRecord() {}

    public CollectionRecord(String groupId, String memberId, String memberName, int month, int year, BigDecimal expectedAmount) {
        this.businessKey = groupId + "_" + memberId + "_" + month + "_" + year;
        this.id = this.businessKey;
        this.groupId = groupId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.month = month;
        this.year = year;
        this.expectedAmount = expectedAmount;
        this.paidAmount = BigDecimal.ZERO;
        this.pendingAmount = expectedAmount;
        this.status = CollectionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public void setExpectedAmount(BigDecimal expectedAmount) { this.expectedAmount = expectedAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getPendingAmount() { return pendingAmount; }
    public void setPendingAmount(BigDecimal pendingAmount) { this.pendingAmount = pendingAmount; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public CollectionStatus getStatus() { return status; }
    public void setStatus(CollectionStatus status) { this.status = status; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public int getDaysLate() { return daysLate; }
    public void setDaysLate(int daysLate) { this.daysLate = daysLate; }
    public BigDecimal getLateFeeAmount() { return lateFeeAmount != null ? lateFeeAmount : BigDecimal.ZERO; }
    public void setLateFeeAmount(BigDecimal lateFeeAmount) { this.lateFeeAmount = lateFeeAmount; }
    public BigDecimal getWaivedLateFee() { return waivedLateFee != null ? waivedLateFee : BigDecimal.ZERO; }
    public void setWaivedLateFee(BigDecimal waivedLateFee) { this.waivedLateFee = waivedLateFee; }
    public BigDecimal getPaidLateFee() { return paidLateFee != null ? paidLateFee : BigDecimal.ZERO; }
    public void setPaidLateFee(BigDecimal paidLateFee) { this.paidLateFee = paidLateFee; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

