package com.bachatgat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonAlias;

public class PaymentOrderRequest {
    private String groupId;
    private String memberId;

    @NotNull(message = "Total payment amount is required")
    @Positive(message = "Payment amount must be greater than zero")
    private BigDecimal amount;

    private String paymentType = "MONTHLY_SAVINGS"; // MONTHLY_SAVINGS, LOAN_EMI, COMBINED, OTHER
    private int month;
    private int year;

    // Component breakdown
    @JsonAlias("shareAmount")
    private BigDecimal monthlyBachatAmount;
    private String loanId;
    private BigDecimal loanPrincipalAmount;
    private BigDecimal loanInterestAmount;
    private BigDecimal extraLoanPaymentAmount;
    private BigDecimal lateFeeAmount;
    private BigDecimal otherAmount;

    private String idempotencyKey;

    public PaymentOrderRequest() {}

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

    public BigDecimal getLateFeeAmount() { return lateFeeAmount; }
    public void setLateFeeAmount(BigDecimal lateFeeAmount) { this.lateFeeAmount = lateFeeAmount; }

    public BigDecimal getOtherAmount() { return otherAmount; }
    public void setOtherAmount(BigDecimal otherAmount) { this.otherAmount = otherAmount; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
