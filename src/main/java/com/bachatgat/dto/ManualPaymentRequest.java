package com.bachatgat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonAlias;

public class ManualPaymentRequest {
    private String groupId;

    @NotNull(message = "Member ID is required")
    private String memberId;

    @NotNull(message = "Payment amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String paymentType = "SAVINGS"; // SAVINGS, LOAN, COMBINED, OTHER
    private int month = LocalDate.now().getMonthValue();
    private int year = LocalDate.now().getYear();

    private LocalDate paymentDate = LocalDate.now();
    private String paymentMethod = "CASH"; // CASH, BANK_TRANSFER, CHEQUE, UPI

    @JsonAlias("shareAmount")
    private BigDecimal monthlyBachatAmount;
    private String loanId;
    private BigDecimal loanPrincipalAmount;
    private BigDecimal loanInterestAmount;
    private BigDecimal lateFeeAmount;
    private boolean waiveLateFee;
    private BigDecimal otherAmount;

    private String referenceNumber;
    private String notes;

    public ManualPaymentRequest() {}

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

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

    public BigDecimal getLateFeeAmount() { return lateFeeAmount; }
    public void setLateFeeAmount(BigDecimal lateFeeAmount) { this.lateFeeAmount = lateFeeAmount; }

    public boolean isWaiveLateFee() { return waiveLateFee; }
    public void setWaiveLateFee(boolean waiveLateFee) { this.waiveLateFee = waiveLateFee; }

    public BigDecimal getOtherAmount() { return otherAmount; }
    public void setOtherAmount(BigDecimal otherAmount) { this.otherAmount = otherAmount; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
