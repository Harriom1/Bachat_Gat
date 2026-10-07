package com.bachatgat.model;

import com.google.cloud.firestore.annotation.DocumentId;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LoanApplication {
    @DocumentId
    private String id;
    private String applicationId; // e.g. LA-2026-001
    private String groupId;
    private String groupName;
    private String memberId;
    private String memberName;
    
    // Member submission
    private BigDecimal requestedAmount;
    private String purpose;
    private int preferredDurationMonths;
    private String optionalMessage;
    private String supportingDocPath;
    
    // Status
    private LoanApplicationStatus status = LoanApplicationStatus.PENDING;

    // Admin Approval fields
    private BigDecimal approvedAmount;
    private BigDecimal approvedInterestRate;
    private int approvedDurationMonths;
    private String repaymentType = "MONTHLY"; // MONTHLY
    private String interestType = "FLAT";     // FLAT or REDUCING
    private String rejectionReason;
    private String adminNotes;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    
    // Resulting loan reference
    private String generatedLoanId;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public LoanApplication() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public int getPreferredDurationMonths() { return preferredDurationMonths; }
    public void setPreferredDurationMonths(int preferredDurationMonths) { this.preferredDurationMonths = preferredDurationMonths; }
    public int getRepaymentPeriodMonths() { return preferredDurationMonths; }
    public void setRepaymentPeriodMonths(int repaymentPeriodMonths) { this.preferredDurationMonths = repaymentPeriodMonths; }
    public String getOptionalMessage() { return optionalMessage; }
    public void setOptionalMessage(String optionalMessage) { this.optionalMessage = optionalMessage; }
    public String getNotes() { return optionalMessage; }
    public void setNotes(String notes) { this.optionalMessage = notes; }
    public String getSupportingDocPath() { return supportingDocPath; }
    public void setSupportingDocPath(String supportingDocPath) { this.supportingDocPath = supportingDocPath; }
    public LoanApplicationStatus getStatus() { return status; }
    public void setStatus(LoanApplicationStatus status) { this.status = status; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    public BigDecimal getApprovedInterestRate() { return approvedInterestRate; }
    public void setApprovedInterestRate(BigDecimal approvedInterestRate) { this.approvedInterestRate = approvedInterestRate; }
    public int getApprovedDurationMonths() { return approvedDurationMonths; }
    public void setApprovedDurationMonths(int approvedDurationMonths) { this.approvedDurationMonths = approvedDurationMonths; }
    public String getRepaymentType() { return repaymentType; }
    public void setRepaymentType(String repaymentType) { this.repaymentType = repaymentType; }
    public String getInterestType() { return interestType; }
    public void setInterestType(String interestType) { this.interestType = interestType; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getGeneratedLoanId() { return generatedLoanId; }
    public void setGeneratedLoanId(String generatedLoanId) { this.generatedLoanId = generatedLoanId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
