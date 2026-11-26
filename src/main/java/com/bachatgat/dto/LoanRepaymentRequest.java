package com.bachatgat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public class LoanRepaymentRequest {
    private BigDecimal loanPenaltyAmount;
    @NotNull(message = "Payment amount is required")
    @Positive(message = "Payment amount must be greater than zero")
    private BigDecimal amount;

    private LocalDate paymentDate = LocalDate.now();
    private String paymentMethod = "CASH"; // CASH, UPI, BANK_TRANSFER
    private String referenceNumber;
    private String notes;
    private String arrangementType = "NORMAL";
    private String approvalNote;

    public LoanRepaymentRequest() {}

    public LoanRepaymentRequest(BigDecimal amount, LocalDate paymentDate, String paymentMethod, String referenceNumber) {
        this.amount = amount;
        this.paymentDate = paymentDate != null ? paymentDate : LocalDate.now();
        this.paymentMethod = paymentMethod;
        this.referenceNumber = referenceNumber;
    }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
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

    public BigDecimal getLoanPenaltyAmount() { return loanPenaltyAmount; }
    public void setLoanPenaltyAmount(BigDecimal value) { this.loanPenaltyAmount = value; }
}
