package com.bachatgat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public class LoanPartialPaymentRequest {
    @NotNull(message = "Payment amount is required")
    @Positive(message = "Payment amount must be greater than zero")
    private BigDecimal amount;
    private BigDecimal penaltyAmount = BigDecimal.ZERO;
    private BigDecimal monthlyBachatAmount = BigDecimal.ZERO;
    private BigDecimal loanPrincipalAmount = BigDecimal.ZERO;
    private BigDecimal loanInterestAmount = BigDecimal.ZERO;
    private boolean payBachatOnly;
    private LocalDate paymentDate = LocalDate.now();
    private String paymentMethod = "CASH";
    private String referenceNumber;
    private String notes;
    private String arrangementType = "NORMAL"; // NORMAL, INTEREST_ONLY, DEFERRED_PRINCIPAL
    private String approvalNote;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getPenaltyAmount() { return penaltyAmount; }
    public void setPenaltyAmount(BigDecimal penaltyAmount) { this.penaltyAmount = penaltyAmount; }
    public BigDecimal getMonthlyBachatAmount() { return monthlyBachatAmount; }
    public void setMonthlyBachatAmount(BigDecimal monthlyBachatAmount) { this.monthlyBachatAmount = monthlyBachatAmount; }
    public BigDecimal getLoanPrincipalAmount() { return loanPrincipalAmount; }
    public void setLoanPrincipalAmount(BigDecimal loanPrincipalAmount) { this.loanPrincipalAmount = loanPrincipalAmount; }
    public BigDecimal getLoanInterestAmount() { return loanInterestAmount; }
    public void setLoanInterestAmount(BigDecimal loanInterestAmount) { this.loanInterestAmount = loanInterestAmount; }
    public boolean isPayBachatOnly() { return payBachatOnly; }
    public void setPayBachatOnly(boolean payBachatOnly) { this.payBachatOnly = payBachatOnly; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getArrangementType() { return arrangementType; }
    public void setArrangementType(String arrangementType) { this.arrangementType = arrangementType; }
    public String getApprovalNote() { return approvalNote; }
    public void setApprovalNote(String approvalNote) { this.approvalNote = approvalNote; }
}
