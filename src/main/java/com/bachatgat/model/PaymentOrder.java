package com.bachatgat.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class PaymentOrder {
    private String orderId;
    private String groupId;
    private String memberId;
    private String memberName;
    private BigDecimal amount;
    private String currency = "INR";
    private String paymentType; // MONTHLY_SAVINGS, LOAN_EMI, COMBINED, MANUAL
    private int month;
    private int year;

    // Breakdown components
    private BigDecimal monthlyBachatAmount = BigDecimal.ZERO;
    private String loanId;
    private BigDecimal loanPrincipalAmount = BigDecimal.ZERO;
    private BigDecimal loanInterestAmount = BigDecimal.ZERO;
    private BigDecimal extraLoanPaymentAmount = BigDecimal.ZERO;
    private BigDecimal lateFeeAmount = BigDecimal.ZERO;
    private BigDecimal otherAmount = BigDecimal.ZERO;

    // Gateway & Verification details
    private String gatewayName = "PAYMENT_GATEWAY";
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String gatewaySignature;
    private String serverVerificationToken;
    private String paymentMethod; // UPI, NET_BANKING, DEBIT_CARD, CASH
    private String referenceNumber;
    private String idempotencyKey;
    private String status = "CREATED"; // CREATED, PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED
    private String receiptNumber;
    private String notes;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime completedAt;

    public PaymentOrder() {}

    public PaymentOrder(String orderId, String groupId, String memberId, String memberName, BigDecimal amount) {
        this.orderId = orderId;
        this.groupId = groupId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.amount = amount;
        this.status = "CREATED";
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }

    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public BigDecimal getMonthlyBachatAmount() { return monthlyBachatAmount; }
    public void setMonthlyBachatAmount(BigDecimal monthlyBachatAmount) { this.monthlyBachatAmount = monthlyBachatAmount; }
    @JsonIgnore public BigDecimal getShareAmount() { return monthlyBachatAmount; }
    @JsonIgnore public void setShareAmount(BigDecimal shareAmount) { this.monthlyBachatAmount = shareAmount; }

    public String getLoanId() { return loanId; }
    public void setLoanId(String loanId) { this.loanId = loanId; }

    public BigDecimal getLoanPrincipalAmount() { return loanPrincipalAmount; }
    public void setLoanPrincipalAmount(BigDecimal loanPrincipalAmount) { this.loanPrincipalAmount = loanPrincipalAmount; }

    public BigDecimal getLoanInterestAmount() { return loanInterestAmount; }
    public void setLoanInterestAmount(BigDecimal loanInterestAmount) { this.loanInterestAmount = loanInterestAmount; }
    public BigDecimal getExtraLoanPaymentAmount() { return extraLoanPaymentAmount; }
    public void setExtraLoanPaymentAmount(BigDecimal extraLoanPaymentAmount) { this.extraLoanPaymentAmount = extraLoanPaymentAmount; }

    public BigDecimal getLateFeeAmount() { return lateFeeAmount; }
    public void setLateFeeAmount(BigDecimal lateFeeAmount) { this.lateFeeAmount = lateFeeAmount; }

    public BigDecimal getOtherAmount() { return otherAmount; }
    public void setOtherAmount(BigDecimal otherAmount) { this.otherAmount = otherAmount; }

    public String getGatewayName() { return gatewayName; }
    public void setGatewayName(String gatewayName) { this.gatewayName = gatewayName; }

    public String getGatewayOrderId() { return gatewayOrderId; }
    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }

    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }

    public String getGatewaySignature() { return gatewaySignature; }
    public void setGatewaySignature(String gatewaySignature) { this.gatewaySignature = gatewaySignature; }

    public String getServerVerificationToken() { return serverVerificationToken; }
    public void setServerVerificationToken(String serverVerificationToken) { this.serverVerificationToken = serverVerificationToken; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
