package com.bachatgat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CollectionPaymentRequest {
    @NotNull(message = "Member ID is required")
    private String memberId;
    
    private int month;
    private int year;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    // OPTION 1: Monthly Bachat / Savings
    @JsonAlias("shareAmount")
    private BigDecimal monthlyBachatAmount;

    // OPTION 2: Loan EMI / Loan Payment
    private String loanId;
    private BigDecimal loanPrincipalAmount;
    private BigDecimal loanInterestAmount;
    private BigDecimal extraLoanPaymentAmount;

    // OPTION 3: Other
    private BigDecimal otherAmount;
    private String otherCategory; // DONATION, FINE, MISC, OTHER

    // Late Payment Charges
    private BigDecimal lateFeeAmount;
    private BigDecimal waivedLateFee;

    // Payment Gateway / Verification & Idempotency
    private String idempotencyKey;
    private String paymentStatus = "SUCCESS"; // INITIATED, PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED

    private LocalDate paymentDate = LocalDate.now();
    private String paymentMethod = "CASH";
    private String referenceNumber;
    private String notes;

    public CollectionPaymentRequest() {}

    // Getters and Setters
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getMonthlyBachatAmount() { return monthlyBachatAmount; }
    public void setMonthlyBachatAmount(BigDecimal monthlyBachatAmount) { this.monthlyBachatAmount = monthlyBachatAmount; }
    public BigDecimal getShareAmount() { return monthlyBachatAmount; }
    public void setShareAmount(BigDecimal shareAmount) { this.monthlyBachatAmount = shareAmount; }
    public String getLoanId() { return loanId; }
    public void setLoanId(String loanId) { this.loanId = loanId; }
    public BigDecimal getLoanPrincipalAmount() { return loanPrincipalAmount; }
    public void setLoanPrincipalAmount(BigDecimal loanPrincipalAmount) { this.loanPrincipalAmount = loanPrincipalAmount; }
    public BigDecimal getLoanInterestAmount() { return loanInterestAmount; }
    public void setLoanInterestAmount(BigDecimal loanInterestAmount) { this.loanInterestAmount = loanInterestAmount; }
    public BigDecimal getExtraLoanPaymentAmount() { return extraLoanPaymentAmount; }
    public void setExtraLoanPaymentAmount(BigDecimal extraLoanPaymentAmount) { this.extraLoanPaymentAmount = extraLoanPaymentAmount; }
    public BigDecimal getOtherAmount() { return otherAmount; }
    public void setOtherAmount(BigDecimal otherAmount) { this.otherAmount = otherAmount; }
    public String getOtherCategory() { return otherCategory; }
    public void setOtherCategory(String otherCategory) { this.otherCategory = otherCategory; }
    public BigDecimal getLateFeeAmount() { return lateFeeAmount; }
    public void setLateFeeAmount(BigDecimal lateFeeAmount) { this.lateFeeAmount = lateFeeAmount; }
    public BigDecimal getWaivedLateFee() { return waivedLateFee; }
    public void setWaivedLateFee(BigDecimal waivedLateFee) { this.waivedLateFee = waivedLateFee; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

